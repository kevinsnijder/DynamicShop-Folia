package org.minecraftsmp.dynamicshop.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Stock decay lets markets recover; the buy floor keeps that recovery from becoming a money source. */
public class StockDecayTest {
    private static final Material FLOODED = Material.COBBLESTONE;
    private static final Material EMPTIED = Material.DIAMOND;
    private final Map<Field, Object> originalSettings = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        for (Field field : ConfigCacheManager.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                originalSettings.put(field, field.get(null));
            }
        }
        ConfigCacheManager.useTimeInflation = false;
        ConfigCacheManager.minPriceMultiplier = 0.25;
        ConfigCacheManager.maxPriceMultiplier = 3.0;
        ConfigCacheManager.minBuyPriceMultiplier = 0.75;
        ConfigCacheManager.stockDecayHalfLifeHours = 24.0;
    }

    @After
    public void tearDown() throws Exception {
        for (Map.Entry<Field, Object> entry : originalSettings.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
        for (Material mat : new Material[] {FLOODED, EMPTIED}) {
            ShopDataManager.itemConfigs.remove(mat);
            ShopDataManager.stockMap.remove(mat);
        }
    }

    @Test
    public void halfLifeHalvesStockAndZeroDisablesDecay() {
        assertEquals(0.5, ShopDataManager.stockDecayFactor(24, 24), 1e-12);
        assertEquals(0.25, ShopDataManager.stockDecayFactor(48, 24), 1e-12);
        assertEquals(1.0, ShopDataManager.stockDecayFactor(24, 0), 0.0);
        assertEquals(1.0, ShopDataManager.stockDecayFactor(0, 24), 0.0);
    }

    @Test
    public void decayMovesStockTowardZeroFromBothSidesWithoutCrossing() {
        ShopItemConfigs.add(FLOODED);
        ShopItemConfigs.add(EMPTIED);
        ShopDataManager.setStockDirect(FLOODED, 2000);
        ShopDataManager.setStockDirect(EMPTIED, -200);

        ShopDataManager.decayAllStock(24);
        assertEquals(1000, ShopDataManager.getStock(FLOODED), 1e-9);
        assertEquals(-100, ShopDataManager.getStock(EMPTIED), 1e-9);

        ShopDataManager.decayAllStock(24 * 40);
        assertEquals(0, ShopDataManager.getStock(FLOODED), 0.0);
        assertEquals(0, ShopDataManager.getStock(EMPTIED), 0.0);
    }

    @Test
    public void buyFloorRaisesOnlyBuyPrices() {
        double buy = ShopDataManager.computeBuyIntegral(1, 1000, 2000, 1000, 0, 0.75, 1.005, 1);
        double sell = ShopDataManager.computeSellIntegral(1, 1000, 1000, 1000, 0, 0.75, 1.005, 0);
        assertEquals(750, buy, 1e-9);
        assertEquals(250, sell, 1e-9);
    }

    @Test
    public void buyingFromAFloodedShopAndSellingAfterRecoveryNeverProfits() {
        Random random = new Random(41771);
        for (int i = 0; i < 20000; i++) {
            double stock = random.nextInt(6001) - 500;
            int quantity = 1 + random.nextInt(2304);
            double max = new double[] {64, 500, 1000}[random.nextInt(3)];
            double cost = ShopDataManager.computeBuyIntegral(
                    1, stock - quantity, stock, max, 0, 0.75, 1.005, 1);
            double recovered = random.nextDouble() * Math.max(0, stock - quantity);
            double payout = 0.7 * ShopDataManager.computeSellIntegral(
                    1, recovered, quantity, max, 0, 0.75, 1.005, 0);
            assertTrue("Selling back after decay must not pay more than the purchase cost",
                    payout <= cost + 1e-8);
        }
    }

    /** Registers plain item configs for the decay loop. */
    private static final class ShopItemConfigs {
        static void add(Material mat) {
            ShopDataManager.itemConfigs.put(mat, new ShopDataManager.ShopItemConfig(
                    1.0, null, null, null, null, false, false, null, null));
        }
    }
}

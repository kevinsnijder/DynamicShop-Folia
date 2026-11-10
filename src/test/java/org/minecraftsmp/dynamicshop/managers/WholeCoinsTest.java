package org.minecraftsmp.dynamicshop.managers;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

/** Whole-coin mode rounds sales down and purchases to the nearest coin, and leaves fractional mode alone. */
public class WholeCoinsTest {

    @After
    public void tearDown() {
        ConfigCacheManager.wholeCoins = false;
    }

    @Test
    public void salesRoundDownAndPurchasesToTheNearestCoin() {
        ConfigCacheManager.wholeCoins = true;
        assertEquals(7.0, ShopDataManager.wholeSell(7.99), 0.0);
        assertEquals(10.0, ShopDataManager.wholeBuy(10.025), 0.0);
        assertEquals(11.0, ShopDataManager.wholeBuy(10.5), 0.0);
        assertEquals(1.0, ShopDataManager.wholeBuy(0.2), 0.0);
        assertEquals(0.0, ShopDataManager.wholeSell(0.875), 0.0);
        assertEquals(56.0, ShopDataManager.wholeSell(64 * 0.875), 0.0);
    }

    @Test
    public void invalidAndFractionalModesPassThrough() {
        ConfigCacheManager.wholeCoins = true;
        assertEquals(-1.0, ShopDataManager.wholeBuy(-1.0), 0.0);
        assertEquals(-1.0, ShopDataManager.wholeSell(-1.0), 0.0);
        ConfigCacheManager.wholeCoins = false;
        assertEquals(10.025, ShopDataManager.wholeBuy(10.025), 0.0);
        assertEquals(7.99, ShopDataManager.wholeSell(7.99), 0.0);
    }

    @Test
    public void wholeCoinsDisplayHasNoDecimals() {
        ConfigCacheManager.wholeCoins = true;
        assertEquals("1,234 coins", CurrencyDisplay.format("<money> <currency>", 1234.0, "coin", "coins"));
        ConfigCacheManager.wholeCoins = false;
        assertEquals("1,234.50 coins", CurrencyDisplay.format("<money> <currency>", 1234.5, "coin", "coins"));
    }
}

package org.minecraftsmp.dynamicshop.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.minecraftsmp.dynamicshop.category.ItemCategory;

/** Sell-only items can be hidden from browsing without becoming unsellable. */
public class HideSellOnlyItemsTest {
    private static final Material BUYABLE = Material.OAK_PLANKS;
    private static final Material SELL_ONLY = Material.OAK_BOAT;
    private boolean originalHide;

    @Before
    public void setUp() {
        originalHide = ConfigCacheManager.hideSellOnlyItems;
        ShopDataManager.itemConfigs.put(BUYABLE, config(false));
        ShopDataManager.itemConfigs.put(SELL_ONLY, config(true));
    }

    @After
    public void tearDown() {
        ConfigCacheManager.hideSellOnlyItems = originalHide;
        ShopDataManager.itemConfigs.remove(BUYABLE);
        ShopDataManager.itemConfigs.remove(SELL_ONLY);
    }

    @Test
    public void sellOnlyItemsAreShownByDefault() {
        ConfigCacheManager.hideSellOnlyItems = false;
        List<Material> items = ShopDataManager.getBrowsableItemsInCategory(ItemCategory.MISC);
        assertTrue(items.contains(BUYABLE));
        assertTrue(items.contains(SELL_ONLY));
    }

    @Test
    public void hidingRemovesSellOnlyItemsFromBrowsingButKeepsThemSellable() {
        ConfigCacheManager.hideSellOnlyItems = true;
        List<Material> items = ShopDataManager.getBrowsableItemsInCategory(ItemCategory.MISC);
        assertTrue(items.contains(BUYABLE));
        assertFalse(items.contains(SELL_ONLY));
        assertTrue(ShopDataManager.getItemsInCategory(ItemCategory.MISC).contains(SELL_ONLY));
        assertTrue(ShopDataManager.canSell(SELL_ONLY, 1));
        assertFalse(ShopDataManager.canBuy(SELL_ONLY, 1));
        assertEquals(
                ShopDataManager.getItemsInCategory(ItemCategory.MISC).size() - 1, items.size());
    }

    private static ShopDataManager.ShopItemConfig config(boolean disableBuy) {
        return new ShopDataManager.ShopItemConfig(
                1.0, null, null, null, null, disableBuy, false, null, null);
    }
}

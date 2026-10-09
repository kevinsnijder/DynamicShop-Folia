package org.minecraftsmp.dynamicshop.placeholder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.replacer.CharsReplacer;
import me.clip.placeholderapi.replacer.Replacer;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import org.minecraftsmp.dynamicshop.transactions.Transaction;
import org.minecraftsmp.dynamicshop.transactions.TransactionLogger;

import static org.junit.Assert.*;

public class DynamicShopExpansionTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private PlaceholderExpansion expansion;
    private DynamicShop plugin;
    private TransactionLogger transactions;
    private Map<Material, Double> stocks;
    private Map<Material, Double> savedStocks;
    private Map<Material, ShopDataManager.ShopItemConfig> savedItems;
    private Field shopPluginField;
    private Object savedShopPlugin;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        plugin = PluginTestFixture.plugin(directory.getRoot());
        transactions = new TransactionLogger(plugin);
        PluginTestFixture.set(plugin, DynamicShop.class, "transactionLogger", transactions);
        // Use the superclass reference to exercise the callback dispatched by PlaceholderAPI.
        expansion = new DynamicShopExpansion(plugin);
        Field stocksField = ShopDataManager.class.getDeclaredField("stockMap");
        stocksField.setAccessible(true);
        stocks = (Map<Material, Double>) stocksField.get(null);
        savedStocks = new HashMap<>(stocks);
        savedItems = new HashMap<>(ShopDataManager.itemConfigs);
        stocks.clear();
        ShopDataManager.itemConfigs.clear();
        shopPluginField = ShopDataManager.class.getDeclaredField("plugin");
        shopPluginField.setAccessible(true);
        savedShopPlugin = shopPluginField.get(null);
        shopPluginField.set(null, plugin);
        ShopDataManager.itemConfigs.put(Material.GOLD_INGOT,
                new ShopDataManager.ShopItemConfig(100, null, null, null, null, false, false, null, null));
    }

    @After
    public void restoreShopState() throws Exception {
        stocks.clear();
        stocks.putAll(savedStocks);
        ShopDataManager.itemConfigs.clear();
        ShopDataManager.itemConfigs.putAll(savedItems);
        shopPluginField.set(null, savedShopPlugin);
    }

    @Test
    public void placeholderApiParsesTheExactReportedPlaceholder() {
        stocks.put(Material.GOLD_INGOT, 400.0);
        CharsReplacer parser = new CharsReplacer(Replacer.Closure.PERCENT);
        String result = parser.apply("Gold stock: %dynamicshop_stock_GOLD_INGOT%", null,
                identifier -> identifier.equals("dynamicshop") ? expansion : null);
        assertEquals("Gold stock: 400", result);
        assertEquals("%dynamicshop_unknown%", parser.apply("%dynamicshop_unknown%", null,
                identifier -> expansion));
    }

    @Test
    public void documentedStockPlaceholderUsesLiveStockAndFullMaterialName() {
        stocks.put(Material.GOLD_INGOT, 400.0);
        assertEquals("400", expansion.onRequest(null, "stock_GOLD_INGOT"));
        stocks.put(Material.GOLD_INGOT, 375.5);
        assertEquals("375.5", expansion.onRequest(null, "stock_gold_ingot"));
        stocks.put(Material.GOLD_INGOT, -12.0);
        assertEquals("-12", expansion.onRequest(null, "stock_GOLD_INGOT"));
        assertTrue(expansion.persist());
    }

    @Test
    public void stockLookupDoesNotDependOnServerLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            stocks.put(Material.GOLD_INGOT, 12345.25);
            assertEquals("12345.25", expansion.onRequest(null, "STOCK_GOLD_INGOT"));
            assertEquals("12345.25", expansion.onRequest(null, "stock_gold_ingot"));
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    public void missingAndInvalidStockReturnZeroAndUnrecognizedPlaceholderReturnsNull() {
        assertEquals("0", expansion.onRequest(null, "stock_STONE"));
        assertEquals("0", expansion.onRequest(null, "stock_NOT_A_MATERIAL"));
        assertEquals("0", expansion.onRequest(null, "stock_"));
        assertNull(expansion.onRequest(null, "not_a_placeholder"));
    }

    @Test
    public void existingGlobalStatsResolveThroughPlaceholderApiCallback() {
        recordTrades();
        assertEquals("2", expansion.onRequest(null, "total_transactions"));
        assertEquals("4", expansion.onRequest(null, "items_bought"));
        assertEquals("7", expansion.onRequest(null, "items_sold"));
        assertEquals("GOLD_INGOT", expansion.onRequest(null, "most_traded_item"));
    }

    @Test
    public void itemStatsPreserveUnderscoresInMaterialsAndStatNames() {
        recordTrades();
        assertEquals("Gold Ingot", expansion.onRequest(null, "item_displayname_GOLD_INGOT"));
        for (String stat : new String[] {"timesbought", "times_bought", "timessold", "times_sold"}) {
            assertEquals("1", expansion.onRequest(null, "item_" + stat + "_GOLD_INGOT"));
        }
        assertEquals("4", expansion.onRequest(null, "item_times_bought_quantity_GOLD_INGOT"));
        assertEquals("7", expansion.onRequest(null, "item_times_sold_quantity_GOLD_INGOT"));
        assertEquals("3", expansion.onRequest(null, "item_net_flow_GOLD_INGOT"));
        assertEquals("3", expansion.onRequest(null, "item_net_GOLD_INGOT"));
        assertEquals("0", expansion.onRequest(null, "item_timesbought_NOT_A_MATERIAL"));
        assertEquals("invalid_format", expansion.onRequest(null, "item_displayname_"));
    }

    @Test
    public void playerStatsPreserveUnderscoresInNamesAndStatNames() {
        recordTrades();
        assertEquals("1", expansion.onRequest(null, "player_test_player_purchases"));
        assertEquals("1", expansion.onRequest(null, "player_TEST_PLAYER_sales"));
        assertEquals("100.00", expansion.onRequest(null, "player_test_player_total_spent"));
        assertEquals("25.00", expansion.onRequest(null, "player_test_player_total_earned"));
        assertEquals("0", expansion.onRequest(null, "player_missing_player_purchases"));
        assertEquals("invalid_format", expansion.onRequest(null, "player_test_player_"));
    }

    @Test
    public void recentTransactionKeepsConfiguredCurrencyDisplay() {
        plugin.getConfig().set("economy.currency_format", "🪙");
        assertEquals("None", expansion.onRequest(null, "recent_transaction"));
        recordTrades();
        String recent = expansion.onRequest(null, "recent_transaction");
        assertTrue(recent, recent.endsWith("🪙 25.00"));
    }

    private void recordTrades() {
        transactions.log(Transaction.now("test_player", Transaction.TransactionType.BUY,
                "GOLD_INGOT", 4, 100, "Minerals", null));
        transactions.log(Transaction.now("test_player", Transaction.TransactionType.SELL,
                "GOLD_INGOT", 7, 25, "Minerals", null));
    }
}

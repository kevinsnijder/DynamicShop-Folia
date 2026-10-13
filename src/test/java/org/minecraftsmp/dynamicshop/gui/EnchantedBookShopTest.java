package org.minecraftsmp.dynamicshop.gui;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.enchantments.Enchantment;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.managers.SpecialShopManager;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;
import org.minecraftsmp.dynamicshop.managers.MultiCurrencyEconomyManager;
import org.minecraftsmp.dynamicshop.listeners.ShopListener;
import org.minecraftsmp.dynamicshop.transactions.TransactionLogger;
import org.minecraftsmp.dynamicshop.support.BookTestStack;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import org.minecraftsmp.dynamicshop.util.PaperCompat;
import org.minecraftsmp.dynamicshop.util.ShopItemNames;
import org.minecraftsmp.dynamicshop.web.WebServer;
import static org.junit.Assert.*;

public class EnchantedBookShopTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private DynamicShop plugin;
    private SpecialShopManager specials;
    private final Map<String, Map<Object, Object>> saved = new HashMap<>();

    @Before public void setUp() throws Exception {
        plugin = PluginTestFixture.plugin(directory.getRoot());
        specials = new SpecialShopManager(plugin);
        PluginTestFixture.set(plugin, DynamicShop.class, "specialShopManager", specials);
        for (String field : new String[] {"itemConfigs", "itemTemplates", "customNames", "variantStockMap", "variantLastUpdateMap", "variantShortageHoursMap", "variantPurchasesMap"}) {
            Map<Object, Object> map = map(field);
            saved.put(field, new HashMap<>(map));
            map.clear();
        }
        ShopDataManager.itemConfigs.put(Material.ENCHANTED_BOOK,
                new ShopDataManager.ShopItemConfig(50, null, null, null, null, false, false, null, null));
    }

    @After public void restore() throws Exception {
        for (var entry : saved.entrySet()) {
            Map<Object, Object> map = map(entry.getKey());
            map.clear();
            map.putAll(entry.getValue());
        }
    }

    @Test public void unnamedBooksShowStoredEnchantmentsAndSearchMatchesThem() {
        BookTestStack book = new BookTestStack(Map.of(Enchantment.MENDING, 1));
        assertEquals("Enchanted Book (Mending I)", ShopItemNames.getDisplayName(book));
        assertTrue(ShopItemNames.matches(ShopItemNames.getDisplayName(book), "mending"));
        assertNull(book.getItemMeta().displayName());
    }

    @Test public void namedBooksKeepTheirNamesAndIncludeEveryStoredEnchantment() {
        BookTestStack book = new BookTestStack(Map.of(Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        var meta = book.getItemMeta();
        meta.displayName(Component.text("Repair Book"));
        book.setItemMeta(meta);
        assertEquals("Repair Book (Mending I, Unbreaking III)", ShopItemNames.getDisplayName(book));
        ShopItemNames.applyBookDisplayName(book, meta);
        ShopItemNames.applyBookDisplayName(book, meta);
        assertEquals("Repair Book (Mending I, Unbreaking III)", PaperCompat.getPlainDisplayName(meta));
        assertEquals("Repair Book", PaperCompat.getPlainDisplayName(book.getItemMeta()));
    }

    @Test public void genericNameCapitalizationAndHighLevelsAreReadable() {
        BookTestStack book = new BookTestStack(Map.of(Enchantment.SHARPNESS, 12));
        var meta = book.getItemMeta();
        meta.displayName(Component.text("ENchanted book"));
        book.setItemMeta(meta);
        assertEquals("Enchanted Book (Sharpness 12)", ShopItemNames.getDisplayName(book));
    }

    @Test public void savedVariantsShowTheSameBookNameInGameAndOnTheWebsite() throws Exception {
        String id = "enchanted_book_custom_1";
        BookTestStack book = new BookTestStack(Map.of(Enchantment.MENDING, 1));
        specials.addServerShopItem(id, id, 25, id, Material.ENCHANTED_BOOK, null, false);
        var entry = specials.getSpecialItem(id);
        entry.setDeliveryMethod("stored_item");
        plugin.getConfig().set("special_items." + id + ".stored_item", book);
        var gui = new ShopGUI(plugin, null, ItemCategory.SERVER_SHOP);
        var icon = gui.buildSpecialShopItem(entry);
        assertEquals("Enchanted Book (Mending I)", PaperCompat.getPlainDisplayName(icon.getItemMeta()));
        var web = new WebServer(plugin);
        var mapper = WebServer.class.getDeclaredMethod("buildCustomAdminItemMap", entry.getClass());
        mapper.setAccessible(true);
        Map<?, ?> row = (Map<?, ?>) mapper.invoke(web, entry);
        assertEquals("Enchanted Book (Mending I)", row.get("displayName"));
        assertEquals(id, row.get("customName"));
        assertEquals(Map.of(Enchantment.MENDING, 1), ((org.bukkit.inventory.meta.EnchantmentStorageMeta) book.getItemMeta()).getStoredEnchants());
        assertNull(book.getItemMeta().displayName());
    }

    @Test public void purchasingSavedBooksKeepsEveryStoredEnchantmentAndDoesNotDeliverMenuLore() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previousServer = serverField.get(null);
        boolean previousDynamic = ConfigCacheManager.dynamicPricingEnabled;
        long previousCooldown = ConfigCacheManager.transactionCooldownMs;
        List<ItemStack> delivered = new ArrayList<>();
        List<Double> charges = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        BukkitScheduler scheduler = PluginTestFixture.proxy(BukkitScheduler.class, (object, method, args) -> {
            if (method.getName().equals("runTaskLater")) return null;
            throw new AssertionError(method.getName());
        });
        try {
            ConfigCacheManager.dynamicPricingEnabled = false;
            ConfigCacheManager.transactionCooldownMs = 0;
            serverField.set(null, PluginTestFixture.proxy(Server.class, (object, method, args) -> {
                if (method.getName().equals("getScheduler")) return scheduler;
                if (method.getName().equals("isPrimaryThread")) return true;
                throw new AssertionError(method.getName());
            }));
            PlayerInventory inventory = PluginTestFixture.proxy(PlayerInventory.class, (object, method, args) -> switch (method.getName()) {
                case "getStorageContents" -> new ItemStack[36];
                case "addItem" -> {
                    for (ItemStack item : (ItemStack[]) args[0]) delivered.add(item.clone());
                    yield new HashMap<Integer, ItemStack>();
                }
                default -> throw new AssertionError(method.getName());
            });
            Player player = PluginTestFixture.proxy(Player.class, (object, method, args) -> switch (method.getName()) {
                case "getInventory" -> inventory;
                case "getName" -> "BookBuyer";
                case "getUniqueId" -> UUID.fromString("00000000-0000-0000-0000-000000000001");
                case "hasPermission" -> false;
                case "sendMessage" -> {
                    messages.add((String) args[0]);
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });
            MultiCurrencyEconomyManager economy = new MultiCurrencyEconomyManager(plugin) {
                @Override public String getCurrency(Material material) { return "coins"; }
                @Override public boolean hasEnough(Player buyer, double amount, String currency) { return true; }
                @Override public boolean charge(Player buyer, double amount, String currency) {
                    charges.add(amount);
                    return true;
                }
            };
            PluginTestFixture.set(plugin, DynamicShop.class, "economyManager", economy);
            PluginTestFixture.set(plugin, DynamicShop.class, "transactionLogger", new TransactionLogger(plugin));
            var messageConfig = new org.bukkit.configuration.file.YamlConfiguration();
            messageConfig.set("messages.bought-item", "Bought {amount} {item}");
            PluginTestFixture.set(plugin.getMessageManager(), org.minecraftsmp.dynamicshop.managers.MessageManager.class, "messagesConfig", messageConfig);
            String id = "repair_book";
            BookTestStack savedBook = new BookTestStack(Map.of(Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3));
            specials.addServerShopItem(id, id, 25, id, Material.ENCHANTED_BOOK, null, false);
            var entry = specials.getSpecialItem(id);
            entry.setDeliveryMethod("stored_item");
            plugin.getConfig().set("special_items." + id + ".stored_item", savedBook);
            ShopDataManager.initializeVariantData(id, Material.ENCHANTED_BOOK);
            map("variantStockMap").put(id, 10.0);
            new ShopGUI(plugin, null, ItemCategory.SERVER_SHOP).buildSpecialShopItem(entry);
            new ShopListener(plugin).buyItem(player, Material.ENCHANTED_BOOK, 2, null,
                    specials.getTradeTemplate(entry), 25, id);
            assertEquals(List.of(50.0), charges);
            assertEquals(2, delivered.size());
            for (ItemStack book : delivered) {
                var meta = (EnchantmentStorageMeta) book.getItemMeta();
                assertEquals(Map.of(Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3), meta.getStoredEnchants());
                assertNull(meta.displayName());
                assertFalse(meta.hasLore());
                assertEquals(1, book.getAmount());
            }
            assertEquals(8.0, ShopDataManager.getVariantStock(id), 0.0);
            assertEquals(List.of("Bought 2 Enchanted Book (Mending I, Unbreaking III)"), messages);
            assertNull(savedBook.getItemMeta().displayName());
            assertFalse(savedBook.getItemMeta().hasLore());
        } finally {
            serverField.set(null, previousServer);
            ConfigCacheManager.dynamicPricingEnabled = previousDynamic;
            ConfigCacheManager.transactionCooldownMs = previousCooldown;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> map(String name) throws Exception {
        Field field = ShopDataManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(null);
    }
}

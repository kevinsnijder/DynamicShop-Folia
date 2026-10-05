package org.minecraftsmp.dynamicshop.managers;

import static org.junit.Assert.*;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.commands.ShopCommand;
import org.minecraftsmp.dynamicshop.listeners.ShopListener;
import org.minecraftsmp.dynamicshop.support.PlainTestStack;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import org.minecraftsmp.dynamicshop.transactions.TransactionLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.*;

public class SalePaymentFailureTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private final Map<Field, Object> settings = new HashMap<>();
    private final Map<Map<Object, Object>, Map<Object, Object>> originalMaps =
            new IdentityHashMap<>();
    private final Map<Set<Object>, Set<Object>> originalSets = new IdentityHashMap<>();
    private final ItemStack[] contents = new ItemStack[41];
    private final List<String> messages = new ArrayList<>();
    private final List<LogRecord> logs = new ArrayList<>();
    private final UUID playerId = UUID.randomUUID();
    private DynamicShop plugin;
    private ShopListener listener;
    private Player player;
    private MultiCurrencyEconomyManager economy;
    private Object previousServer;
    private Object previousDataPlugin;
    private int handSlot;
    private int depositCalls;
    private double credited;
    private String mode = "FAILURE";

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        for (Field field : ConfigCacheManager.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && !Modifier.isFinal(field.getModifiers())) {
                settings.put(field, field.get(null));
            }
        }
        for (Field field : ShopDataManager.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map) {
                originalMaps.put(
                        (Map<Object, Object>) map, new HashMap<>((Map<Object, Object>) map));
            } else if (value instanceof Set<?> set) {
                originalSets.put((Set<Object>) set, new HashSet<>((Set<Object>) set));
            }
        }
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        BukkitScheduler scheduler =
                PluginTestFixture.proxy(
                        BukkitScheduler.class,
                        (object, method, args) -> {
                            if (method.getName().equals("runTaskLater")) return null;
                            if (method.getName().equals("runTaskAsynchronously")) return null;
                            throw new AssertionError(method.getName());
                        });
        serverField.set(
                null,
                PluginTestFixture.proxy(
                        Server.class,
                        (object, method, args) -> {
                            if (method.getName().equals("getScheduler")) return scheduler;
                            if (method.getName().equals("isPrimaryThread")) return true;
                            throw new AssertionError(method.getName());
                        }));
        Field dataPlugin = ShopDataManager.class.getDeclaredField("plugin");
        dataPlugin.setAccessible(true);
        previousDataPlugin = dataPlugin.get(null);
        plugin = PluginTestFixture.plugin(directory.getRoot());
        dataPlugin.set(null, plugin);
        plugin.getLogger().setUseParentHandlers(false);
        plugin.getLogger().setLevel(Level.ALL);
        plugin.getLogger()
                .addHandler(
                        new Handler() {
                            @Override
                            public void publish(LogRecord record) {
                                logs.add(record);
                            }

                            @Override
                            public void flush() {}

                            @Override
                            public void close() {}
                        });
        YamlConfiguration messageConfig = new YamlConfiguration();
        messageConfig.set("messages.sold-item-success", "Sold {amount}x {item} for {price}");
        PluginTestFixture.set(
                plugin.getMessageManager(), MessageManager.class, "messagesConfig", messageConfig);
        listener = new ShopListener(plugin);
        PluginTestFixture.set(plugin, DynamicShop.class, "shopListener", listener);
        PluginTestFixture.set(
                plugin, DynamicShop.class, "transactionLogger", new TransactionLogger(plugin));
        Economy provider =
                PluginTestFixture.proxy(
                        Economy.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "getName" -> "TestEconomy";
                                    case "format" -> Double.toString((double) args[0]);
                                    case "depositPlayer" -> {
                                        depositCalls++;
                                        double amount = (double) args[1];
                                        if (mode.equals("THROW_AFTER_CREDIT")) {
                                            credited += amount;
                                            throw new IllegalStateException("response lost");
                                        }
                                        if (mode.equals("NULL")) yield null;
                                        if (mode.equals("SUCCESS")
                                                || (mode.equals("MIXED") && amount == 400)) {
                                            credited += amount;
                                            yield new EconomyResponse(
                                                    amount,
                                                    credited,
                                                    EconomyResponse.ResponseType.SUCCESS,
                                                    "");
                                        }
                                        yield new EconomyResponse(
                                                0,
                                                credited,
                                                EconomyResponse.ResponseType.FAILURE,
                                                "Balance limit\nreached");
                                    }
                                    default -> throw new AssertionError(method.getName());
                                });
        economy =
                new MultiCurrencyEconomyManager(plugin) {
                    @Override
                    public String getCurrency(Material material) {
                        return material.name();
                    }
                };
        PluginTestFixture.set(economy, MultiCurrencyEconomyManager.class, "vaultEconomy", provider);
        PluginTestFixture.set(plugin, DynamicShop.class, "economyManager", economy);
        PlayerInventory inventory =
                PluginTestFixture.proxy(
                        PlayerInventory.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "getContents" -> contents.clone();
                                    case "getSize" -> contents.length;
                                    case "getItem" -> contents[(int) args[0]];
                                    case "setItem" -> {
                                        contents[(int) args[0]] = (ItemStack) args[1];
                                        yield null;
                                    }
                                    case "getItemInMainHand" -> contents[handSlot];
                                    case "setItemInMainHand" -> {
                                        contents[handSlot] = (ItemStack) args[0];
                                        yield null;
                                    }
                                    default -> throw new AssertionError(method.getName());
                                });
        player =
                PluginTestFixture.proxy(
                        Player.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "getInventory" -> inventory;
                                    case "hasPermission" -> true;
                                    case "getUniqueId" -> playerId;
                                    case "getName" -> "Seller";
                                    case "sendMessage" -> {
                                        messages.add(String.valueOf(args[0]));
                                        yield null;
                                    }
                                    default -> throw new AssertionError(method.getName());
                                });
        ConfigCacheManager.dynamicPricingEnabled = false;
        ConfigCacheManager.useTimeInflation = false;
        ConfigCacheManager.highInflationCorrectionEnabled = false;
        ConfigCacheManager.sellTaxPercent = 0.8;
        ConfigCacheManager.transactionCooldownMs = 0;
        configureItem(Material.GOLDEN_HORSE_ARMOR, 100_000, "Iridium Watering Can");
        configureItem(Material.DIAMOND, 2_000, "Diamond");
    }

    @After
    public void tearDown() throws Exception {
        for (Map.Entry<Field, Object> setting : settings.entrySet())
            setting.getKey().set(null, setting.getValue());
        originalMaps.forEach(
                (map, original) -> {
                    map.clear();
                    map.putAll(original);
                });
        originalSets.forEach(
                (set, original) -> {
                    set.clear();
                    set.addAll(original);
                });
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, previousServer);
        Field dataPlugin = ShopDataManager.class.getDeclaredField("plugin");
        dataPlugin.setAccessible(true);
        dataPlugin.set(null, previousDataPlugin);
    }

    @SuppressWarnings("unchecked")
    private void configureItem(Material material, double price, String name) throws Exception {
        ShopDataManager.itemConfigs.put(
                material,
                new ShopDataManager.ShopItemConfig(
                        price, null, null, null, null, false, false, null, null));
        Field templates = ShopDataManager.class.getDeclaredField("itemTemplates");
        templates.setAccessible(true);
        ((Map<Material, ItemStack>) templates.get(null))
                .put(material, new PlainTestStack(material, 1));
        Field names = ShopDataManager.class.getDeclaredField("customNames");
        names.setAccessible(true);
        ((Map<Material, String>) names.get(null)).put(material, name);
    }

    private void resetSale() throws Exception {
        PluginTestFixture.set(
                plugin,
                DynamicShop.class,
                "featureMetrics",
                new org.minecraftsmp.dynamicshop.metrics.FeatureMetrics(plugin));
        Arrays.fill(contents, null);
        contents[0] = new PlainTestStack(Material.GOLDEN_HORSE_ARMOR, 1);
        ShopDataManager.stockMap.put(Material.GOLDEN_HORSE_ARMOR, 0.0);
        messages.clear();
        logs.clear();
        depositCalls = 0;
        credited = 0;
        PluginTestFixture.set(
                plugin, DynamicShop.class, "transactionLogger", new TransactionLogger(plugin));
    }

    private void sell(String path) {
        if (path.equals("GUI")) listener.sellItem(player, Material.GOLDEN_HORSE_ARMOR, 1, null);
        else new ShopCommand(plugin).onCommand(player, null, "shop", path.split(" "));
    }

    @Test
    public void rejectedOrUncertainSalesLogDetailsWithoutRestoringRetryingOrReportingSuccess()
            throws Exception {
        for (String result : List.of("FAILURE", "NULL", "THROW_AFTER_CREDIT")) {
            mode = result;
            for (String path : List.of("GUI", "sellhand", "sellall", "sellall hand")) {
                resetSale();
                sell(path);
                assertNull(path, contents[0]);
                assertEquals(1, depositCalls);
                assertEquals(1.0, ShopDataManager.getStock(Material.GOLDEN_HORSE_ARMOR), 0);
                assertEquals(result.equals("THROW_AFTER_CREDIT") ? 20_000 : 0, credited, 0);
                assertTrue(plugin.getTransactionLogger().getRecentTransactions().isEmpty());
                assertEquals(0, plugin.getFeatureMetrics().transactionSnapshot().total());
                assertEquals(1, messages.size());
                assertTrue(messages.get(0).contains("could not be confirmed"));
                assertEquals(1, logs.size());
                LogRecord log = logs.get(0);
                assertEquals(Level.SEVERE, log.getLevel());
                for (String detail :
                        List.of(
                                "player=Seller",
                                playerId.toString(),
                                "1x Iridium Watering Can",
                                "[GOLDEN_HORSE_ARMOR]",
                                "amount=20000.0",
                                "currency=GOLDEN_HORSE_ARMOR",
                                "provider=Vault/TestEconomy",
                                "items and stock were not restored",
                                "no payment retry")) {
                    assertTrue(detail, log.getMessage().contains(detail));
                }
                assertTrue(
                        log.getMessage()
                                .contains(
                                        "outcome="
                                                + (result.equals("FAILURE")
                                                        ? "FAILED"
                                                        : "UNKNOWN")));
                assertFalse(log.getMessage().contains("\n"));
                assertTrue(
                        log.getMessage()
                                .contains(
                                        result.equals("FAILURE")
                                                ? "Balance limit reached"
                                                : result.equals("NULL")
                                                        ? "Provider returned no response"
                                                        : "response lost"));
                sell(path);
                assertEquals("Empty inventory must not initiate another deposit", 1, depositCalls);
            }
        }
    }

    @Test
    public void successfulSalesStillPayAndRecordExactlyOnce() throws Exception {
        mode = "SUCCESS";
        for (String path : List.of("GUI", "sellhand", "sellall", "sellall hand")) {
            resetSale();
            sell(path);
            sell(path);
            assertEquals(1, depositCalls);
            assertEquals(20_000, credited, 0);
            assertNull(contents[0]);
            assertEquals(1, plugin.getTransactionLogger().getRecentTransactions().size());
            assertEquals(1, plugin.getFeatureMetrics().transactionSnapshot().sells());
            assertTrue(logs.isEmpty());
            assertEquals(1, messages.stream().filter(message -> message.contains("Sold")).count());
        }
    }

    @Test
    public void sellAllReportsOnlySuccessfullyPaidCurrencyGroups() throws Exception {
        mode = "MIXED";
        resetSale();
        contents[1] = new PlainTestStack(Material.DIAMOND, 1);
        sell("sellall");
        assertEquals(2, depositCalls);
        assertEquals(400, credited, 0);
        assertNull(contents[0]);
        assertNull(contents[1]);
        assertEquals(1, logs.size());
        assertEquals(1, plugin.getTransactionLogger().getRecentTransactions().size());
        assertEquals(
                "DIAMOND", plugin.getTransactionLogger().getRecentTransactions().get(0).getItem());
        assertEquals(1, plugin.getFeatureMetrics().transactionSnapshot().total());
        String success =
                messages.stream()
                        .filter(message -> message.contains("Sold"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(success.contains("1 items"));
        assertTrue(success.contains("400.0"));
        assertFalse(success.contains("20000"));
    }

    @Test
    public void sellAllHandSellsEveryMatchingStackAndLeavesOtherMaterials() throws Exception {
        mode = "SUCCESS";
        resetSale();
        contents[0].setAmount(3);
        contents[9] = new PlainTestStack(Material.GOLDEN_HORSE_ARMOR, 4);
        contents[35] = new PlainTestStack(Material.GOLDEN_HORSE_ARMOR, 2);
        ItemStack diamonds = contents[1] = new PlainTestStack(Material.DIAMOND, 10);
        sell("sellall hand");
        assertNull(contents[0]);
        assertNull(contents[9]);
        assertNull(contents[35]);
        assertSame(diamonds, contents[1]);
        assertEquals(10, diamonds.getAmount());
        assertEquals(1, depositCalls);
        assertEquals(180_000, credited, 0);
        assertEquals(9, ShopDataManager.getStock(Material.GOLDEN_HORSE_ARMOR), 0);
        var sales = plugin.getTransactionLogger().getRecentTransactions();
        assertEquals(1, sales.size());
        assertEquals("GOLDEN_HORSE_ARMOR", sales.get(0).getItem());
        assertEquals(9, sales.get(0).getAmount());
    }

    @Test
    public void sellAllHandRespectsStockCapacityAndRetainsTheRemainder() throws Exception {
        mode = "SUCCESS";
        resetSale();
        ShopDataManager.itemConfigs.put(
                Material.GOLDEN_HORSE_ARMOR,
                new ShopDataManager.ShopItemConfig(
                        100_000, null, null, 5, null, false, false, null, null));
        ShopDataManager.stockMap.put(Material.GOLDEN_HORSE_ARMOR, 1.0);
        contents[0].setAmount(3);
        contents[9] = new PlainTestStack(Material.GOLDEN_HORSE_ARMOR, 4);
        sell("sellall hand");
        assertNull(contents[0]);
        assertEquals(3, contents[9].getAmount());
        assertEquals(5, ShopDataManager.getStock(Material.GOLDEN_HORSE_ARMOR), 0);
        assertEquals(80_000, credited, 0);
        contents[0] = contents[9];
        contents[9] = null;
        sell("sellall hand");
        assertEquals(1, depositCalls);
        assertEquals(3, contents[0].getAmount());
    }

    @Test
    public void emptyOrUnsellableHandNeverFallsBackToSellingOtherItems() throws Exception {
        for (ItemStack held :
                Arrays.asList(null, new PlainTestStack(Material.AIR, 1), new UnmatchedStack())) {
            resetSale();
            contents[0] = held;
            ItemStack other = contents[1] = new PlainTestStack(Material.GOLDEN_HORSE_ARMOR, 4);
            sell("sellall hand");
            assertSame(held, contents[0]);
            assertSame(other, contents[1]);
            assertEquals(4, other.getAmount());
            assertEquals(0, depositCalls);
            assertEquals(0, ShopDataManager.getStock(Material.GOLDEN_HORSE_ARMOR), 0);
        }
    }

    @Test
    public void sellAllHandSkipsDifferentMetadataOnTheSameMaterial() throws Exception {
        mode = "SUCCESS";
        resetSale();
        ItemStack custom = contents[1] = new UnmatchedStack();
        sell("sellall hand");
        assertNull(contents[0]);
        assertSame(custom, contents[1]);
        assertEquals(1, depositCalls);
        assertEquals(20_000, credited, 0);
    }

    @Test
    public void sellAllHandLeavesDisabledItemsAndOtherMaterialsAlone() throws Exception {
        resetSale();
        ShopDataManager.itemConfigs.put(
                Material.GOLDEN_HORSE_ARMOR,
                new ShopDataManager.ShopItemConfig(
                        100_000, null, null, null, null, false, true, null, null));
        contents[1] = new PlainTestStack(Material.DIAMOND, 2);
        sell("sellall hand");
        assertEquals(1, contents[0].getAmount());
        assertEquals(2, contents[1].getAmount());
        assertEquals(0, depositCalls);
    }

    private SpecialShopManager customItems() throws Exception {
        SpecialShopManager manager =
                new SpecialShopManager(plugin) {
                    @Override
                    public ItemStack getTradeTemplate(
                            org.minecraftsmp.dynamicshop.category.SpecialShopItem item) {
                        if (item == null || !item.isTradeItem()) return null;
                        return new CustomStack(item.getId(), 1);
                    }
                };
        PluginTestFixture.set(plugin, DynamicShop.class, "specialShopManager", manager);
        manager.addServerShopItem(
                "can", "Custom can", 100, "can", Material.GOLDEN_HORSE_ARMOR, null, true);
        plugin.getConfig().set("special_items.can.delivery_method", "oraxen");
        plugin.getConfig().set("special_items.can.nbt", "iridium_can");
        plugin.getConfig().set("special_items.can.category", "CUSTOM_1");
        manager.reload();
        ShopDataManager.setVariantStockDirect("can", 0);
        return manager;
    }

    @Test
    public void physicalCustomProvidersTradeWhileCommandRewardsRemainPurchaseOnly() {
        var item =
                org.minecraftsmp.dynamicshop.category.SpecialShopItem.forServerShop(
                        "can", "Can", 100, "can", Material.GOLDEN_HORSE_ARMOR, null);
        for (String provider :
                List.of("oraxen", "nexo", "itemsadder", "valhallammo", "stored_item")) {
            item.setDeliveryMethod(provider);
            assertTrue(provider, item.isTradeItem());
        }
        for (String delivery : List.of("item", "command", "nbt", "component")) {
            item.setDeliveryMethod(delivery);
            assertFalse(delivery, item.isTradeItem());
        }
    }

    @Test
    public void customHandSalesUseCustomPriceAndIndependentStock() throws Exception {
        mode = "SUCCESS";
        resetSale();
        customItems();
        contents[0] = new CustomStack("can", 3);
        sell("sellhand");
        assertNull(contents[0]);
        assertEquals(60, credited, 0.000001);
        assertEquals(3, ShopDataManager.getVariantStock("can"), 0);
        assertEquals(0, ShopDataManager.getStock(Material.GOLDEN_HORSE_ARMOR), 0);
        var sale = plugin.getTransactionLogger().getRecentTransactions().getFirst();
        assertEquals("VARIANT:can", sale.getItem());
        assertEquals("CUSTOM_1", sale.getCategory());
    }

    @Test
    public void customSellAllHandKeepsVanillaAndOtherCustomItemsOnTheSameMaterial()
            throws Exception {
        mode = "SUCCESS";
        resetSale();
        customItems();
        contents[0] = new CustomStack("can", 2);
        contents[1] = new CustomStack("can", 4);
        contents[2] = new PlainTestStack(Material.GOLDEN_HORSE_ARMOR, 5);
        contents[3] = new CustomStack("other", 6);
        sell("sellall hand");
        assertNull(contents[0]);
        assertNull(contents[1]);
        assertEquals(5, contents[2].getAmount());
        assertEquals(6, contents[3].getAmount());
        assertEquals(120, credited, 0.000001);
        assertEquals(6, ShopDataManager.getVariantStock("can"), 0);
    }

    @Test
    public void mixedSellAllGroupsCustomAndVanillaPayoutsWithoutMergingStock() throws Exception {
        mode = "SUCCESS";
        resetSale();
        customItems();
        contents[0] = new CustomStack("can", 2);
        contents[1] = new PlainTestStack(Material.GOLDEN_HORSE_ARMOR, 1);
        sell("sellall");
        assertNull(contents[0]);
        assertNull(contents[1]);
        assertEquals(20_040, credited, 0.000001);
        assertEquals(1, depositCalls);
        assertEquals(2, ShopDataManager.getVariantStock("can"), 0);
        assertEquals(1, ShopDataManager.getStock(Material.GOLDEN_HORSE_ARMOR), 0);
    }

    @Test
    public void customSalesHonorOwnStorageAndDisableFlagsWithoutDependingOnVanillaConfig()
            throws Exception {
        mode = "SUCCESS";
        resetSale();
        customItems();
        ShopDataManager.itemConfigs.remove(Material.GOLDEN_HORSE_ARMOR);
        plugin.getConfig().set("special_items.can.max-stock-storage", 2);
        contents[0] = new CustomStack("can", 5);
        sell("sellall hand");
        assertEquals(3, contents[0].getAmount());
        assertEquals(40, credited, 0.000001);
        plugin.getConfig().set("special_items.can.disable-sell", true);
        sell("sellall");
        assertEquals(3, contents[0].getAmount());
        assertEquals(1, depositCalls);
    }

    @Test
    public void guiCustomSaleUsesCurrentPriceAndRejectsDeletedEntries() throws Exception {
        mode = "SUCCESS";
        resetSale();
        var manager = customItems();
        contents[0] = new CustomStack("can", 3);
        listener.sellItem(
                player,
                Material.GOLDEN_HORSE_ARMOR,
                1,
                null,
                new CustomStack("wrong", 1),
                999_999,
                "can");
        assertEquals(2, contents[0].getAmount());
        assertEquals(20, credited, 0.000001);
        manager.removeSpecialItem("can");
        listener.sellItem(
                player,
                Material.GOLDEN_HORSE_ARMOR,
                1,
                null,
                new CustomStack("can", 1),
                100,
                "can");
        assertEquals(2, contents[0].getAmount());
        assertEquals(1, depositCalls);
    }

    @Test
    public void unavailableProviderNeverResolvesToVanillaTemplate() throws Exception {
        customItems();
        var realManager = new SpecialShopManager(plugin);
        realManager.addServerShopItem(
                "missing", "Missing", 100, "missing", Material.GOLDEN_HORSE_ARMOR, null, true);
        plugin.getConfig().set("special_items.missing.delivery_method", "stored_item");
        realManager.reload();
        assertNull(realManager.getTradeTemplate(realManager.getSpecialItem("missing")));
    }

    @Test
    public void customAdminApiListsEditsAndDeletesEntriesAndRejectsInvalidWrites()
            throws Exception {
        PluginTestFixture.set(
                plugin, org.bukkit.plugin.java.JavaPlugin.class, "server", Bukkit.getServer());
        Field dataConfig = ShopDataManager.class.getDeclaredField("shopDataConfig");
        Field dataFile = ShopDataManager.class.getDeclaredField("shopDataFile");
        dataConfig.setAccessible(true);
        dataFile.setAccessible(true);
        Object oldConfig = dataConfig.get(null), oldFile = dataFile.get(null);
        dataConfig.set(null, new YamlConfiguration());
        dataFile.set(null, new java.io.File(directory.getRoot(), "shopdata.yml"));
        var manager = customItems();
        PluginTestFixture.set(
                plugin, DynamicShop.class, "playerShopManager", new PlayerShopManager(plugin));
        var web = new org.minecraftsmp.dynamicshop.web.WebServer(plugin);
        web.getUserManager().register("Admin", "testing123");
        String session = web.getUserManager().login("Admin", "testing123");
        try (var app =
                io.javalin.Javalin.create()
                        .before("/api/admin/*", ctx -> invokeWeb(web, "requireAdmin", ctx))
                        .get("/api/admin/items", ctx -> invokeWeb(web, "handleAdminItems", ctx))
                        .get(
                                "/api/admin/item/{item}",
                                ctx -> invokeWeb(web, "handleAdminItemDetail", ctx))
                        .post(
                                "/api/admin/item/{item}",
                                ctx -> invokeWeb(web, "handleAdminItemUpdate", ctx))
                        .post(
                                "/api/admin/items/bulk",
                                ctx -> invokeWeb(web, "handleAdminItemsBulkUpdate", ctx))
                        .delete(
                                "/api/admin/item/{item}",
                                ctx -> invokeWeb(web, "handleAdminItemRemove", ctx))
                        .start("127.0.0.1", 0)) {
            String base = "http://127.0.0.1:" + app.port();
            var client = java.net.http.HttpClient.newHttpClient();
            var denied =
                    webRequest(
                            client,
                            base,
                            "/api/admin/item/special:can",
                            "POST",
                            null,
                            "{\"basePrice\":5}");
            assertEquals(401, denied.statusCode());
            assertEquals(100, manager.getSpecialItem("can").getPrice(), 0);
            var list = webRequest(client, base, "/api/admin/items", "GET", session, null);
            assertEquals(200, list.statusCode());
            assertTrue(list.body().contains("special:can"));
            assertTrue(list.body().contains("\"tradeItem\":true"));
            var update =
                    webRequest(
                            client,
                            base,
                            "/api/admin/item/special:can",
                            "POST",
                            session,
                            "{\"basePrice\":250,\"stock\":12,\"maxStockStorage\":20,\"stockRate\":7,\"category\":\"FOOD\",\"sellDisabled\":true,\"displayName\":\"Better"
                                + " can\"}");
            assertEquals(
                    update.body() + logs.stream().map(LogRecord::getMessage).toList(),
                    200,
                    update.statusCode());
            var saved = manager.getSpecialItem("can");
            assertEquals(250, saved.getPrice(), 0);
            assertEquals(
                    org.minecraftsmp.dynamicshop.category.ItemCategory.FOOD, saved.getCategory());
            assertEquals("oraxen", saved.getDeliveryMethod());
            assertEquals("iridium_can", saved.getNbt());
            assertEquals(12, ShopDataManager.getVariantStock("can"), 0);
            assertEquals(100_000, ShopDataManager.getBasePrice(Material.GOLDEN_HORSE_ARMOR), 0);
            assertFalse(manager.canTrade(player, "can", false));
            var detail =
                    webRequest(client, base, "/api/admin/item/special:can", "GET", session, null);
            assertEquals(200, detail.statusCode());
            var json = new com.google.gson.Gson().fromJson(detail.body(), Map.class);
            assertEquals(12.0, json.get("stock"));
            assertEquals(250.0, json.get("basePrice"));
            assertEquals(true, json.get("sellDisabled"));
            var invalid =
                    webRequest(
                            client,
                            base,
                            "/api/admin/item/special:can",
                            "POST",
                            session,
                            "{\"basePrice\":999,\"stock\":21}");
            assertEquals(400, invalid.statusCode());
            assertEquals(250, manager.getSpecialItem("can").getPrice(), 0);
            var bulk =
                    webRequest(
                            client,
                            base,
                            "/api/admin/items/bulk",
                            "POST",
                            session,
                            "{\"items\":[\"special:can\",\"GOLDEN_HORSE_ARMOR\"],\"updates\":{\"sellDisabled\":false}}");
            assertEquals(bulk.body(), 200, bulk.statusCode());
            assertTrue(manager.canTrade(player, "can", false));
            var removed =
                    webRequest(
                            client, base, "/api/admin/item/special:can", "DELETE", session, null);
            assertEquals(removed.body(), 200, removed.statusCode());
            assertNull(manager.getSpecialItem("can"));
            assertFalse(plugin.getConfig().contains("special_items.can"));
            manager.reload();
            assertNull(manager.getSpecialItem("can"));
            assertEquals(
                    404,
                    webRequest(client, base, "/api/admin/item/special:can", "GET", session, null)
                            .statusCode());
        } finally {
            dataConfig.set(null, oldConfig);
            dataFile.set(null, oldFile);
        }
    }

    private static void invokeWeb(
            org.minecraftsmp.dynamicshop.web.WebServer web,
            String name,
            io.javalin.http.Context ctx)
            throws Exception {
        var method = web.getClass().getDeclaredMethod(name, io.javalin.http.Context.class);
        method.setAccessible(true);
        try {
            method.invoke(web, ctx);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }

    private static java.net.http.HttpResponse<String> webRequest(
            java.net.http.HttpClient client,
            String base,
            String path,
            String method,
            String session,
            String body)
            throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path));
        if (session != null) request.header("X-Session-Token", session);
        request.header("Content-Type", "application/json");
        request.method(
                method,
                body == null
                        ? java.net.http.HttpRequest.BodyPublishers.noBody()
                        : java.net.http.HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void rightClickCustomItemsTradesInRegularAndServerShopCategories() throws Exception {
        mode = "SUCCESS";
        ConfigCacheManager.useDialogGui = false;
        var manager = customItems();
        var click =
                ShopListener.class.getDeclaredMethod(
                        "handleShopClick",
                        Player.class,
                        org.minecraftsmp.dynamicshop.gui.ShopGUI.class,
                        int.class,
                        boolean.class,
                        boolean.class);
        click.setAccessible(true);
        for (var category :
                List.of(
                        org.minecraftsmp.dynamicshop.category.ItemCategory.CUSTOM_1,
                        org.minecraftsmp.dynamicshop.category.ItemCategory.SERVER_SHOP)) {
            resetSale();
            ShopDataManager.setVariantStockDirect("can", 0);
            contents[0] = new CustomStack("can", 6);
            contents[1] = new PlainTestStack(Material.GOLDEN_HORSE_ARMOR, 2);
            TradeGui gui = new org.objenesis.ObjenesisStd().newInstance(TradeGui.class);
            gui.entry = manager.getSpecialItem("can");
            gui.entry.setCategory(category);
            click.invoke(listener, player, gui, 10, true, true);
            assertNull(contents[0]);
            assertEquals(2, contents[1].getAmount());
            assertEquals(120, credited, 0.000001);
            assertEquals(6, ShopDataManager.getVariantStock("can"), 0);
        }
    }

    @Test
    public void rightClickPlayerInventoryResolvesCustomItemInsteadOfItsVanillaMaterial()
            throws Exception {
        mode = "SUCCESS";
        resetSale();
        customItems();
        contents[0] = new CustomStack("can", 6);
        var click =
                ShopListener.class.getDeclaredMethod(
                        "handlePlayerInventoryClick",
                        Player.class,
                        ItemStack.class,
                        boolean.class,
                        boolean.class);
        click.setAccessible(true);
        click.invoke(listener, player, contents[0], true, true);
        assertNull(contents[0]);
        assertEquals(120, credited, 0.000001);
        assertEquals(6, ShopDataManager.getVariantStock("can"), 0);
    }

    @Test
    public void customSellHandRemovesOnlyTheActualHeldStack() throws Exception {
        mode = "SUCCESS";
        resetSale();
        customItems();
        handSlot = 5;
        contents[0] = new CustomStack("can", 2);
        contents[5] = new CustomStack("can", 3);
        sell("sellhand");
        assertNull(contents[5]);
        assertEquals(2, contents[0].getAmount());
        assertEquals(60, credited, 0.000001);
    }

    private static final class TradeGui extends org.minecraftsmp.dynamicshop.gui.ShopGUI {
        private org.minecraftsmp.dynamicshop.category.SpecialShopItem entry;

        private TradeGui() {
            super(null, null, null);
        }

        @Override
        public org.minecraftsmp.dynamicshop.category.ItemCategory getCategory() {
            return entry.getCategory();
        }

        @Override
        public Material getItemFromSlot(int slot) {
            return null;
        }

        @Override
        public org.minecraftsmp.dynamicshop.category.SpecialShopItem getSpecialItemFromSlot(
                int slot) {
            return entry;
        }

        @Override
        public boolean isNavigationSlot(int slot) {
            return false;
        }

        @Override
        public void render() {
            /* Packet rendering is outside this transaction fixture. */
        }
    }

    private static final class CustomStack extends ItemStack {
        private final String id;
        private int count;

        CustomStack(String id, int count) {
            this.id = id;
            this.count = count;
        }

        @Override
        public Material getType() {
            return Material.GOLDEN_HORSE_ARMOR;
        }

        @Override
        public int getAmount() {
            return count;
        }

        @Override
        public void setAmount(int value) {
            count = value;
        }

        @Override
        public boolean hasItemMeta() {
            return false;
        }

        @Override
        public boolean isSimilar(ItemStack other) {
            return other instanceof CustomStack stack && id.equals(stack.id);
        }

        @Override
        public CustomStack clone() {
            return new CustomStack(id, count);
        }
    }

    private static final class UnmatchedStack extends ItemStack {
        private int amount = 1;

        @Override
        public Material getType() {
            return Material.GOLDEN_HORSE_ARMOR;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public void setAmount(int value) {
            amount = value;
        }

        @Override
        public boolean hasItemMeta() {
            return false;
        }

        @Override
        public boolean isSimilar(ItemStack other) {
            return false;
        }

        @Override
        public UnmatchedStack clone() {
            return new UnmatchedStack();
        }
    }

    public static class CoinsApi {
        static int calls;

        public static Object getCurrency(String id) {
            return new Object();
        }

        public static boolean accept(Player player, Object currency, double amount) {
            calls++;
            return true;
        }

        public static boolean reject(Player player, Object currency, double amount) {
            calls++;
            return false;
        }

        public static void acceptVoid(Player player, Object currency, double amount) {
            calls++;
        }

        public static void throwAfterCall(Player player, Object currency, double amount) {
            calls++;
            throw new IllegalStateException("CoinsEngine response lost");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void coinsEngineResultsAndInvocationErrorsAreHandledWithoutRetry() throws Exception {
        PluginTestFixture.set(economy, MultiCurrencyEconomyManager.class, "useCoinEngine", true);
        PluginTestFixture.set(
                economy,
                MultiCurrencyEconomyManager.class,
                "coinEngineGetCurrencyMethod",
                CoinsApi.class.getMethod("getCurrency", String.class));
        Field cacheField =
                MultiCurrencyEconomyManager.class.getDeclaredField("coinEngineCurrencyCache");
        cacheField.setAccessible(true);
        ((Map<String, Object>) cacheField.get(economy)).put("coins", new Object());
        for (String method : List.of("accept", "reject", "acceptVoid", "throwAfterCall")) {
            CoinsApi.calls = 0;
            logs.clear();
            messages.clear();
            PluginTestFixture.set(
                    economy,
                    MultiCurrencyEconomyManager.class,
                    "coinEngineAddPlayerBalanceMethod",
                    CoinsApi.class.getMethod(method, Player.class, Object.class, double.class));
            boolean paid = economy.depositSale(player, 20_000, "coins", "1x Iridium Watering Can");
            assertEquals(method.startsWith("accept"), paid);
            assertEquals(1, CoinsApi.calls);
            if (paid) {
                assertTrue(logs.isEmpty());
                assertTrue(messages.isEmpty());
            } else {
                assertEquals(1, logs.size());
                assertEquals(1, messages.size());
                assertTrue(
                        logs.get(0)
                                .getMessage()
                                .contains(
                                        method.equals("reject")
                                                ? "outcome=FAILED"
                                                : "outcome=UNKNOWN"));
                assertTrue(
                        logs.get(0)
                                .getMessage()
                                .contains(
                                        method.equals("reject")
                                                ? "CoinsEngine returned false"
                                                : "CoinsEngine response lost"));
            }
        }
    }
}

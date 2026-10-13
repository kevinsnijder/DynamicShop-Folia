package org.minecraftsmp.dynamicshop.gui;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.category.SpecialShopItem;
import org.minecraftsmp.dynamicshop.managers.MultiCurrencyEconomyManager;
import org.minecraftsmp.dynamicshop.managers.PermissionsManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.managers.SpecialShopManager;
import org.minecraftsmp.dynamicshop.support.BookTestStack;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import org.minecraftsmp.dynamicshop.support.TestPermissionProvider;
import org.minecraftsmp.dynamicshop.transactions.TransactionLogger;
import static org.junit.Assert.*;

public class RewardShopTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private DynamicShop plugin;
    private SpecialShopManager specials;
    private TestPermissionProvider provider;
    private Player player;
    private final List<Double> charges = new ArrayList<>();
    private final List<Double> refunds = new ArrayList<>();
    private Map<Material, ShopDataManager.ShopItemConfig> savedItems;
    private Field serverField;
    private Object previousServer;

    @Before public void setUp() throws Exception {
        savedItems = new HashMap<>(ShopDataManager.itemConfigs);
        ShopDataManager.itemConfigs.put(Material.ENDER_CHEST,
                new ShopDataManager.ShopItemConfig(80, null, null, null, null, false, false, null, null));
        plugin = PluginTestFixture.plugin(directory.getRoot());
        plugin.getConfig().set("economy.currency_format", "🪙");
        provider = new TestPermissionProvider();
        var permissions = new PermissionsManager(plugin);
        PluginTestFixture.set(permissions, PermissionsManager.class, "vaultPerms", provider);
        PluginTestFixture.set(plugin, DynamicShop.class, "permissionsManager", permissions);
        PluginTestFixture.set(plugin, DynamicShop.class, "transactionLogger", new TransactionLogger(plugin));
        var economy = new MultiCurrencyEconomyManager(plugin) {
            @Override public boolean charge(Player buyer, double price) {
                charges.add(price);
                return true;
            }
            @Override public void deposit(Player buyer, double price) {
                refunds.add(price);
            }
        };
        PluginTestFixture.set(plugin, DynamicShop.class, "economyManager", economy);
        specials = new SpecialShopManager(plugin);
        PluginTestFixture.set(plugin, DynamicShop.class, "specialShopManager", specials);
        player = PluginTestFixture.proxy(Player.class, (object, method, args) -> switch (method.getName()) {
            case "getName" -> "UpgradeBuyer";
            case "getUniqueId" -> UUID.fromString("00000000-0000-0000-0000-000000000002");
            case "hasPermission" -> provider.permissions(null).contains((String) args[0]);
            case "sendMessage" -> null;
            default -> throw new AssertionError(method.getName());
        });
        ItemFactory factory = PluginTestFixture.proxy(ItemFactory.class, (object, method, args) -> switch (method.getName()) {
            case "getItemMeta" -> new BookTestStack(Map.of()).getItemMeta();
            case "asMetaFor" -> ((ItemMeta) args[0]).clone();
            case "isApplicable" -> true;
            case "equals" -> args[0] == args[1];
            default -> throw new AssertionError(method.getName());
        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, PluginTestFixture.proxy(Server.class, (object, method, args) -> {
            if (method.getName().equals("getItemFactory")) return factory;
            if (method.getName().equals("getLogger")) return plugin.getLogger();
            throw new AssertionError(method.getName());
        }));
    }

    @After public void restore() throws Exception {
        ShopDataManager.itemConfigs.clear();
        ShopDataManager.itemConfigs.putAll(savedItems);
        serverField.set(null, previousServer);
    }

    @Test public void rewardIconsUseConfiguredPriceWithoutSellingOrMaterialInflation() {
        for (SpecialShopItem entry : entries()) {
            List<String> lore = new ShopGUI(plugin, player, ItemCategory.PERMISSIONS)
                    .buildSpecialShopItem(entry).getItemMeta().getLore();
            assertTrue(lore.toString(), lore.stream().anyMatch(line -> line.contains("31.00")));
            assertFalse(lore.toString(), lore.stream().anyMatch(line -> line.contains("SELL") || line.contains("80.00") || line.contains("Stock") || line.contains("increase")));
        }
    }

    @Test public void changingRewardPriceUpdatesTheMenuPrice() {
        var entry = entries().get(0);
        specials.updateItemPrice(entry.getId(), 42);
        var current = specials.getSpecialItem(entry.getId());
        List<String> lore = new ShopGUI(plugin, player, ItemCategory.PERMISSIONS)
                .buildSpecialShopItem(current).getItemMeta().getLore();
        assertTrue(lore.toString(), lore.stream().anyMatch(line -> line.contains("42.00")));
        assertFalse(lore.toString(), lore.stream().anyMatch(line -> line.contains("31.00") || line.contains("80.00")));
    }

    @Test public void alreadyOwnedPermissionsAndGroupsAreRejectedBeforeCharging() {
        provider.permissions(null).add("enderchest.upgrade1");
        provider.groups(null).add("ENDUPGRADE1");
        for (SpecialShopItem entry : entries()) {
            assertTrue(specials.ownsReward(player, entry));
            var icon = new ShopGUI(plugin, player, ItemCategory.PERMISSIONS).buildSpecialShopItem(entry);
            assertTrue(icon.getItemMeta().getLore().toString().contains("already own"));
            specials.purchase(player, entry);
        }
        assertTrue(charges.isEmpty());
        assertEquals(0, provider.grants);
    }

    @Test public void firstPurchaseSucceedsAndRepeatingItDoesNotChargeAgainInAnotherCategory() {
        for (SpecialShopItem entry : entries()) {
            entry.setCategory(ItemCategory.CUSTOM_1);
            specials.purchase(player, entry);
            assertTrue(specials.ownsReward(player, entry));
            specials.purchase(player, entry);
        }
        assertEquals(List.of(31.0, 31.0), charges);
        assertEquals(2, provider.grants);
        assertTrue(refunds.isEmpty());
        assertEquals(2, plugin.getTransactionLogger().getTotalTransactions());
    }

    @Test public void worldScopedOwnershipDoesNotUseAnotherWorldsPermissions() {
        var permissions = plugin.getPermissionsManager();
        provider.permissions(null).add("enderchest.upgrade1");
        provider.groups("nether").add("EndUpgrade1");
        assertFalse(permissions.hasPermission(player, "enderchest.upgrade1", "world"));
        assertTrue(permissions.isInGroup(player, "endupgrade1", "nether"));
        assertFalse(permissions.isInGroup(player, "endupgrade1", "world"));
    }

    @Test public void failedPermissionAndGroupGrantsRefundTheConfiguredPrice() {
        provider.allowGrants = false;
        for (SpecialShopItem entry : entries()) {
            specials.purchase(player, entry);
            assertFalse(specials.ownsReward(player, entry));
        }
        assertEquals(List.of(31.0, 31.0), charges);
        assertEquals(charges, refunds);
        assertEquals(0, plugin.getTransactionLogger().getTotalTransactions());
    }

    private List<SpecialShopItem> entries() {
        specials.addPermissionItem("enderchest.upgrade1", null, 31, Material.ENDER_CHEST, null);
        specials.addGroupItem("EndUpgrade1", null, 31, Material.ENDER_CHEST, null);
        return List.of(specials.getSpecialItem("enderchest_upgrade1"), specials.getSpecialItem("group_endupgrade1"));
    }
}

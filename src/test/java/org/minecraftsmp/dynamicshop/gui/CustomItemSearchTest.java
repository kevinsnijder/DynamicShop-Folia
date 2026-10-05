package org.minecraftsmp.dynamicshop.gui;

import static org.junit.Assert.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.category.SpecialShopItem;
import org.minecraftsmp.dynamicshop.listeners.ShopListener;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.managers.SpecialShopManager;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import org.minecraftsmp.dynamicshop.util.ShopItemNames;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomItemSearchTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private static final Material BASE = Material.GOLDEN_HORSE_ARMOR;
    private final Map<String, Map<?, ?>> originals = new HashMap<>();
    private DynamicShop plugin;
    private final Map<String, ItemStack> customTemplates = new HashMap<>();
    private SpecialShopManager specials;

    @Before
    public void setUp() throws Exception {
        for (String field : List.of("itemConfigs", "itemTemplates", "customNames")) {
            Map<?, ?> map = map(field);
            originals.put(field, new HashMap<>(map));
            map.clear();
        }
        ShopDataManager.itemConfigs.put(
                BASE,
                new ShopDataManager.ShopItemConfig(
                        100, null, null, null, null, false, false, null, null));
        plugin = PluginTestFixture.plugin(directory.getRoot());
        PluginTestFixture.set(plugin, DynamicShop.class, "shopListener", new ShopListener(plugin));
        specials =
                new SpecialShopManager(plugin) {
                    @Override
                    public ItemStack getTradeTemplate(SpecialShopItem item) {
                        return item == null ? null : customTemplates.get(item.getId());
                    }
                };
        PluginTestFixture.set(plugin, DynamicShop.class, "specialShopManager", specials);
    }

    @After
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void tearDown() throws Exception {
        for (var entry : originals.entrySet()) {
            Map map = map(entry.getKey());
            map.clear();
            map.putAll(entry.getValue());
        }
    }

    @Test
    public void itemNameTemplateMatchesVisibleNameButNeverBaseMaterial() throws Exception {
        template(null, Component.text("Iridium Watering Can", NamedTextColor.LIGHT_PURPLE), false);
        assertEquals("Iridium Watering Can", ShopItemNames.getDisplayName(BASE));
        for (String query :
                List.of(
                        "iridium",
                        "watering can",
                        "IRIDIUM_WATERING_CAN",
                        "  iridium   watering  can ")) {
            assertEquals(List.of(BASE), search(query));
        }
        for (String query :
                List.of("golden horse armor", "GOLDEN_HORSE_ARMOR", "horse", "armor", "copper")) {
            assertTrue(search(query).isEmpty());
        }
    }

    @Test
    public void legacyDisplayNameTakesPriorityOverItemName() throws Exception {
        template(
                Component.text("Copper Watering Can"),
                Component.text("Iridium Watering Can"),
                false);
        assertEquals(List.of(BASE), search("copper watering can"));
        assertTrue(search("iridium").isEmpty());
        assertTrue(search("golden horse armor").isEmpty());
    }

    @Test
    public void configuredNameTakesPriorityAndFormattingIsNotSearchable() throws Exception {
        template(null, Component.text("Iridium Watering Can"), false);
        map("customNames").put(BASE, "&6Garden &lSprinkler");
        assertEquals("Garden Sprinkler", ShopItemNames.getDisplayName(BASE));
        assertEquals(List.of(BASE), search("garden sprinkler"));
        assertTrue(search("iridium").isEmpty());
        assertTrue(search("golden_horse_armor").isEmpty());
        assertTrue(search("&6").isEmpty());
    }

    @Test
    public void unnamedVanillaItemsStillMatchMaterialNamesWithSpacesOrUnderscores()
            throws Exception {
        for (String query : List.of("golden horse armor", "golden_horse_armor", "horse")) {
            assertEquals(List.of(BASE), search(query));
        }
        assertTrue(search("iridium").isEmpty());
        template(null, null, false);
        assertEquals(List.of(BASE), search("golden horse armor"));
    }

    @Test
    public void spigotItemNameFallbackAlsoFindsTheCustomItem() throws Exception {
        template(null, Component.text("Iridium Watering Can"), true);
        assertEquals(List.of(BASE), search("iridium watering can"));
        assertTrue(search("golden horse armor").isEmpty());
    }

    @Test
    public void transactionNameUsesTheDeliveredVariantInsteadOfTheBaseTemplate() throws Exception {
        template(null, Component.text("Iridium Watering Can"), false);
        ItemStack variant = ShopDataManager.getTemplate(BASE);
        template(null, Component.text("Copper Watering Can"), false);
        map("customNames").put(BASE, "&6Base Shop Rename");
        assertEquals("Iridium Watering Can", ShopItemNames.getDisplayName(BASE, variant));
        assertEquals("Base Shop Rename", ShopItemNames.getDisplayName(BASE, null));
    }

    @Test
    public void registeredCustomItemsMatchProviderNamesAcrossAllPhysicalDeliveryMethods()
            throws Exception {
        template(null, Component.text("Iridium Watering Can", NamedTextColor.LIGHT_PURPLE), false);
        for (String provider :
                List.of("oraxen", "nexo", "itemsadder", "valhallammo", "stored_item")) {
            custom(provider, provider, provider, ItemCategory.CUSTOM_1);
            customTemplates.put(provider, ShopDataManager.getTemplate(BASE));
        }
        for (String query :
                List.of("watering can", "IRIDIUM_WATERING_CAN", " iridium  watering can ")) {
            assertEquals(5, specialSearch(query, ItemCategory.CUSTOM_1).size());
            assertEquals(5, specialSearch(query, ItemCategory.MISC).size());
            assertEquals(5, specialSearch(query, null).size());
        }
        assertTrue(specialSearch("golden horse armor", null).isEmpty());
        assertTrue(specialSearch("watering", ItemCategory.FOOD).isEmpty());
    }

    @Test
    public void customSearchUsesConfiguredNamesWhenUnnamedAndExcludesHiddenItemsAndRewards()
            throws Exception {
        custom("can", "&6Watering &lCan", "stored_item", ItemCategory.SERVER_SHOP);
        custom("hidden", "Watering Can", "oraxen", ItemCategory.SERVER_SHOP);
        custom("reward", "Watering Can", "command", ItemCategory.SERVER_SHOP);
        plugin.getConfig().set("special_items.hidden.disabled", true);
        assertEquals(List.of("can"), specialSearch("WATERING_CAN", ItemCategory.SERVER_SHOP));
        assertTrue(specialSearch("&6", null).isEmpty());
        assertTrue(specialSearch("golden horse armor", null).isEmpty());
    }

    @Test
    public void customResultClicksKeepTheirIdAndSearchParentIncludingLaterPages() throws Exception {
        for (int index = 0; index < 46; index++) {
            custom(
                    String.format("can_%02d", index),
                    "Watering Can",
                    "stored_item",
                    ItemCategory.CUSTOM_1);
        }
        List<String> clicked = new java.util.ArrayList<>();
        List<Object> parents = new java.util.ArrayList<>();
        PluginTestFixture.set(
                plugin,
                DynamicShop.class,
                "shopListener",
                new ShopListener(plugin) {
                    @Override
                    public void handleItemClick(
                            org.bukkit.entity.Player player,
                            Object gui,
                            Material mat,
                            SpecialShopItem item,
                            boolean right,
                            boolean shift) {
                        assertNull(mat);
                        clicked.add(item.getId() + ":" + right + ":" + shift);
                        parents.add(gui);
                    }
                });
        SearchResultsGUI gui = gui("watering", ItemCategory.CUSTOM_1);
        gui.handleClick(0, false, false);
        gui.handleClick(53, false, false);
        gui.handleClick(0, true, true);
        gui.handleClick(1, false, false); // Empty slot on the final page.
        gui.handleClick(45, false, false);
        gui.handleClick(0, true, false);
        assertEquals(
                List.of("can_00:false:false", "can_45:true:true", "can_00:true:false"), clicked);
        assertTrue(parents.stream().allMatch(parent -> parent == gui));
    }

    private void custom(String id, String name, String delivery, ItemCategory category) {
        specials.addServerShopItem(id, name, 100, id, BASE, null, true);
        plugin.getConfig().set("special_items." + id + ".delivery_method", delivery);
        plugin.getConfig().set("special_items." + id + ".category", category.name());
        specials.reload();
    }

    @SuppressWarnings("unchecked")
    private List<String> specialSearch(String query, ItemCategory category) throws Exception {
        Field field = SearchResultsGUI.class.getDeclaredField("specialResults");
        field.setAccessible(true);
        return ((List<SpecialShopItem>) field.get(gui(query, category)))
                .stream().map(SpecialShopItem::getId).toList();
    }

    private SearchResultsGUI gui(String query, ItemCategory category) {
        return new SearchResultsGUI(plugin, null, query, category) {
            @Override
            public void open() {} // Exercise filtering without a server inventory.

            @Override
            public void render() {} // Exercise pagination without protocol packets.
        };
    }

    private void template(Component displayName, Component itemName, boolean legacyOnly)
            throws Exception {
        ItemMeta meta =
                PluginTestFixture.proxy(
                        ItemMeta.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "hasDisplayName" -> displayName != null;
                                    case "hasItemName" -> itemName != null;
                                    case "displayName" -> displayName;
                                    case "itemName" -> {
                                        if (legacyOnly)
                                            throw new UnsupportedOperationException(
                                                    "Spigot fallback");
                                        yield itemName;
                                    }
                                    case "getItemName" -> "§dIridium Watering Can";
                                    default ->
                                            throw new AssertionError(
                                                    "Unexpected metadata access: "
                                                            + method.getName());
                                });
        map("itemTemplates").put(BASE, new NamedStack(meta));
    }

    @SuppressWarnings("unchecked")
    private List<Material> search(String query) throws Exception {
        SearchResultsGUI gui = gui(query, ItemCategory.MISC);
        Field field = SearchResultsGUI.class.getDeclaredField("results");
        field.setAccessible(true);
        return (List<Material>) field.get(gui);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> map(String name) throws Exception {
        Field field = ShopDataManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(null);
    }

    private static final class NamedStack extends ItemStack {
        private final ItemMeta meta;

        NamedStack(ItemMeta meta) {
            super();
            this.meta = meta;
        }

        @Override
        public Material getType() {
            return BASE;
        }

        @Override
        public boolean hasItemMeta() {
            return true;
        }

        @Override
        public ItemMeta getItemMeta() {
            return meta;
        }

        @Override
        public NamedStack clone() {
            return new NamedStack(meta);
        }
    }
}

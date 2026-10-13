package org.minecraftsmp.dynamicshop.support;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;

/** A test-only registry so stored-book tests can use Bukkit's actual enchantment API. */
public final class TestRegistryAccess implements RegistryAccess {
    private final Map<NamespacedKey, Enchantment> enchantments = new HashMap<>();

    @Override
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return registry(type == Enchantment.class, type == ItemType.class);
    }

    @Override
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        return registry(key == RegistryKey.ENCHANTMENT, key == RegistryKey.ITEM);
    }

    private <T extends Keyed> Registry<T> registry(boolean enchantmentRegistry, boolean itemRegistry) {
        return PluginTestFixture.proxy(Registry.class, (object, method, args) -> switch (method.getName()) {
            case "get", "getOrThrow" -> {
                NamespacedKey key = (NamespacedKey) args[0];
                if (itemRegistry) yield itemType(key);
                if (!enchantmentRegistry) throw new AssertionError("Unexpected registry lookup: " + key);
                Enchantment enchantment = enchantments.get(key);
                if (enchantment == null) {
                    enchantment = new TestEnchantment(key);
                    enchantments.put(key, enchantment);
                }
                yield enchantment;
            }
            case "iterator" -> enchantments.values().iterator();
            case "stream" -> enchantments.values().stream();
            case "getKey" -> ((Keyed) args[0]).getKey();
            case "size" -> enchantments.size();
            default -> throw new AssertionError("Unexpected registry access: " + method.getName());
        });
    }

    private static ItemType itemType(NamespacedKey key) {
        return PluginTestFixture.proxy(ItemType.Typed.class, (object, method, args) -> switch (method.getName()) {
            case "getKey" -> key;
            case "typed" -> object;
            case "asMaterial" -> Material.valueOf(key.getKey().toUpperCase(java.util.Locale.ROOT));
            case "getItemMetaClass" -> org.bukkit.inventory.meta.ItemMeta.class;
            case "createItemStack" -> {
                var item = new BookTestStack(Material.valueOf(key.getKey().toUpperCase(java.util.Locale.ROOT)), Map.of());
                if (args != null && args.length > 0) item.setAmount((int) args[0]);
                yield item;
            }
            default -> throw new AssertionError("Unexpected item type operation: " + method.getName());
        });
    }

    private static final class TestEnchantment extends Enchantment {
        private final NamespacedKey key;
        TestEnchantment(NamespacedKey key) { this.key = key; }
        @Override public NamespacedKey getKey() { return key; }
        @Override public String getName() { return key.getKey(); }
        @Override public int getMaxLevel() { return 5; }
        @Override public int getStartLevel() { return 1; }
        @Override public EnchantmentTarget getItemTarget() { return EnchantmentTarget.ALL; }
        @Override public boolean isTreasure() { return false; }
        @Override public boolean isCursed() { return key.getKey().endsWith("curse"); }
        @Override public boolean conflictsWith(Enchantment other) { return false; }
        @Override public boolean canEnchantItem(ItemStack item) { return true; }
        @Override public Component displayName(int level) { return Component.text(key.getKey() + " " + level); }
        @Override public boolean isTradeable() { return true; }
        @Override public boolean isDiscoverable() { return true; }
        @Override public int getMinModifiedCost(int level) { return 0; }
        @Override public int getMaxModifiedCost(int level) { return 0; }
        @Override public int getAnvilCost() { return 1; }
        @Override public io.papermc.paper.enchantments.EnchantmentRarity getRarity() { return null; }
        @Override public float getDamageIncrease(int level, EntityCategory category) { return 0; }
        @Override public float getDamageIncrease(int level, EntityType type) { return 0; }
        @Override public Set<EquipmentSlotGroup> getActiveSlotGroups() { return Set.of(); }
        @Override public Component description() { return Component.text(key.getKey()); }
        @Override public RegistryKeySet<ItemType> getSupportedItems() { return null; }
        @Override public RegistryKeySet<ItemType> getPrimaryItems() { return null; }
        @Override public int getWeight() { return 1; }
        @Override public RegistryKeySet<Enchantment> getExclusiveWith() { return null; }
        @Override public String translationKey() { return "enchantment." + key.getNamespace() + "." + key.getKey(); }
        @Override public String getTranslationKey() { return translationKey(); }
    }
}

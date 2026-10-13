package org.minecraftsmp.dynamicshop.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

/** Book metadata and cloning without a server's CraftItemStack implementation. */
public final class BookTestStack extends ItemStack {
    private State state;
    private final Material material;
    private int amount = 1;

    public BookTestStack(Map<Enchantment, Integer> enchantments) {
        this(Material.ENCHANTED_BOOK, enchantments);
    }

    public BookTestStack(Material material, Map<Enchantment, Integer> enchantments) {
        super();
        this.material = material;
        state = new State();
        state.enchantments.putAll(enchantments);
    }

    @Override public Material getType() { return material; }
    @Override public int getAmount() { return amount; }
    @Override public void setAmount(int amount) { this.amount = amount; }
    @Override public int getMaxStackSize() { return material == Material.ENCHANTED_BOOK ? 1 : 64; }
    @Override public boolean hasItemMeta() { return true; }
    @Override public ItemMeta getItemMeta() { return metadata(state.copy()); }

    @Override
    public boolean setItemMeta(ItemMeta meta) {
        state = new State();
        state.enchantments.putAll(((EnchantmentStorageMeta) meta).getStoredEnchants());
        state.displayName = meta.displayName();
        state.itemName = meta.itemName();
        if (meta.lore() != null) state.lore.addAll(meta.lore());
        return true;
    }

    @Override
    public BookTestStack clone() {
        BookTestStack result = new BookTestStack(material, state.enchantments);
        result.state = state.copy();
        result.amount = amount;
        return result;
    }

    @Override
    public boolean isSimilar(ItemStack other) {
        return other instanceof BookTestStack book && material == book.material && state.enchantments.equals(book.state.enchantments)
                && java.util.Objects.equals(state.displayName, book.state.displayName)
                && java.util.Objects.equals(state.itemName, book.state.itemName) && state.lore.equals(book.state.lore);
    }

    private static EnchantmentStorageMeta metadata(State state) {
        return PluginTestFixture.proxy(EnchantmentStorageMeta.class, (object, method, args) -> switch (method.getName()) {
            case "getStoredEnchants" -> Map.copyOf(state.enchantments);
            case "hasStoredEnchants" -> !state.enchantments.isEmpty();
            case "hasStoredEnchant" -> state.enchantments.containsKey(args[0]);
            case "getStoredEnchantLevel" -> state.enchantments.getOrDefault(args[0], 0);
            case "hasDisplayName" -> state.displayName != null;
            case "hasItemName" -> state.itemName != null;
            case "hasLore" -> !state.lore.isEmpty();
            case "displayName" -> {
                if (args == null || args.length == 0) yield state.displayName;
                state.displayName = (Component) args[0];
                yield null;
            }
            case "itemName" -> {
                if (args == null || args.length == 0) yield state.itemName;
                state.itemName = (Component) args[0];
                yield null;
            }
            case "lore" -> {
                if (args == null || args.length == 0) yield new ArrayList<>(state.lore);
                state.lore.clear();
                if (args[0] != null) state.lore.addAll((List<Component>) args[0]);
                yield null;
            }
            case "getLore" -> state.lore.stream().map(LegacyComponentSerializer.legacySection()::serialize).toList();
            case "clone" -> metadata(state.copy());
            default -> throw new AssertionError("Unexpected book metadata operation: " + method.getName());
        });
    }

    private static final class State {
        final Map<Enchantment, Integer> enchantments = new HashMap<>();
        final List<Component> lore = new ArrayList<>();
        Component displayName;
        Component itemName;
        State copy() {
            State copy = new State();
            copy.enchantments.putAll(enchantments);
            copy.lore.addAll(lore);
            copy.displayName = displayName;
            copy.itemName = itemName;
            return copy;
        }
    }
}

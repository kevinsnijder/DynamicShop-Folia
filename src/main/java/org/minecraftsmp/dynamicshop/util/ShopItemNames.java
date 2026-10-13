package org.minecraftsmp.dynamicshop.util;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.minecraftsmp.dynamicshop.category.SpecialShopItem;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;

import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.stream.Collectors;

/** Visible item names for search, dialogs, and transaction messages. */
public final class ShopItemNames {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final List<String> ROMAN_LEVELS = List.of("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X");

    private ShopItemNames() {}

    public static String getDisplayName(Material material, ItemStack deliveryOverride) {
        return deliveryOverride != null ? getDisplayName(deliveryOverride) : getDisplayName(material);
    }

    public static String getDisplayName(Material material) {
        ItemStack template = ShopDataManager.getTemplate(material);
        String configured = ShopDataManager.getCustomName(material);
        if (configured != null) {
            return bookName(PLAIN.serialize(MessageManager.parseComponent(configured)), template);
        }
        return template != null ? getDisplayName(template) : materialName(material);
    }

    public static String getDisplayName(ItemStack item) {
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null) {
            if (meta.hasDisplayName()) {
                return bookName(PLAIN.serialize(PaperCompat.getDisplayName(meta)), item);
            }
            if (meta.hasItemName()) {
                return bookName(PLAIN.serialize(PaperCompat.getItemName(meta)), item);
            }
        }
        return bookName(materialName(item.getType()), item);
    }

    public static String getDisplayName(SpecialShopItem item, ItemStack template) {
        ItemMeta meta = template != null && template.hasItemMeta() ? template.getItemMeta() : null;
        if (meta != null && (meta.hasDisplayName() || meta.hasItemName())) {
            return getDisplayName(template);
        }
        String configured = item.getDisplayName();
        if (configured != null && !configured.equals(item.getId())) {
            return bookName(PLAIN.serialize(MessageManager.parseComponent(configured)), template);
        }
        if (template != null) return getDisplayName(template);
        return item.getDisplayMaterial() != null
                ? materialName(item.getDisplayMaterial())
                : item.getId();
    }

    public static boolean matches(String displayName, String query) {
        return normalize(displayName).contains(normalize(query));
    }

    /** Only decorate menu copies; the saved and delivered book metadata stays intact. */
    public static void applyBookDisplayName(ItemStack item, ItemMeta meta) {
        if (item.getType() != Material.ENCHANTED_BOOK) return;
        String enchantments = storedEnchantments(meta);
        if (enchantments.isEmpty()) return;
        Component name = meta.hasDisplayName() ? PaperCompat.getDisplayName(meta)
                : meta.hasItemName() ? PaperCompat.getItemName(meta)
                        : MessageManager.parseComponent("§e§lEnchanted Book");
        if (normalize(PLAIN.serialize(name)).equals("enchanted book")) {
            name = Component.text("Enchanted Book").style(name.style());
        }
        String suffix = " (" + enchantments + ")";
        if (!PLAIN.serialize(name).endsWith(suffix)) name = name.append(Component.text(suffix));
        PaperCompat.setDisplayName(meta, name);
    }

    private static String bookName(String name, ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) return name;
        String enchantments = storedEnchantments(item.getItemMeta());
        if (enchantments.isEmpty()) return name;
        if (normalize(name).equals("enchanted book")) name = "Enchanted Book";
        String suffix = " (" + enchantments + ")";
        return name.endsWith(suffix) ? name : name + suffix;
    }

    private static String storedEnchantments(ItemMeta meta) {
        if (!(meta instanceof EnchantmentStorageMeta book)) return "";
        return book.getStoredEnchants().entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().getKey().toString()))
                .map(entry -> {
                    String key = entry.getKey().getKey().getKey();
                    String name = switch (key) {
                        case "binding_curse" -> "Curse of Binding";
                        case "vanishing_curse" -> "Curse of Vanishing";
                        default -> titleCase(key);
                    };
                    int level = entry.getValue();
                    return name + " " + (level > 0 && level < ROMAN_LEVELS.size() ? ROMAN_LEVELS.get(level) : level);
                })
                .collect(Collectors.joining(", "));
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('_', ' ').trim().replaceAll("\\s+", " ");
    }

    private static String materialName(Material material) {
        return titleCase(material.name());
    }

    private static String titleCase(String key) {
        return Arrays.stream(key.toLowerCase(Locale.ROOT).split("_"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}

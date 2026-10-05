package org.minecraftsmp.dynamicshop.util;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.category.SpecialShopItem;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Visible item names for search, dialogs, and transaction messages. */
public final class ShopItemNames {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ShopItemNames() {}

    public static String getDisplayName(Material material, ItemStack deliveryOverride) {
        return deliveryOverride != null ? getDisplayName(deliveryOverride) : getDisplayName(material);
    }

    public static String getDisplayName(Material material) {
        String configured = ShopDataManager.getCustomName(material);
        if (configured != null) {
            return PLAIN.serialize(MessageManager.parseComponent(configured));
        }
        ItemStack template = ShopDataManager.getTemplate(material);
        return template != null ? getDisplayName(template) : materialName(material);
    }

    public static String getDisplayName(ItemStack item) {
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null) {
            if (meta.hasDisplayName()) {
                return PLAIN.serialize(PaperCompat.getDisplayName(meta));
            }
            if (meta.hasItemName()) {
                return PLAIN.serialize(PaperCompat.getItemName(meta));
            }
        }
        return materialName(item.getType());
    }

    public static String getDisplayName(SpecialShopItem item, ItemStack template) {
        ItemMeta meta = template != null && template.hasItemMeta() ? template.getItemMeta() : null;
        if (meta != null && (meta.hasDisplayName() || meta.hasItemName())) {
            return getDisplayName(template);
        }
        String configured = item.getDisplayName();
        if (configured != null && !configured.equals(item.getId())) {
            return PLAIN.serialize(MessageManager.parseComponent(configured));
        }
        return item.getDisplayMaterial() != null
                ? materialName(item.getDisplayMaterial())
                : item.getId();
    }

    public static boolean matches(String displayName, String query) {
        return normalize(displayName).contains(normalize(query));
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('_', ' ').trim().replaceAll("\\s+", " ");
    }

    private static String materialName(Material material) {
        return Arrays.stream(material.name().toLowerCase(Locale.ROOT).split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}

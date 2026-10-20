package org.minecraftsmp.dynamicshop.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;
import static org.junit.Assert.*;

public class MessageFormattingTest {
    @Test public void dialogLabelsRenderNamedColorsWithoutAProvider() {
        assertEquals(Component.text("Quantity", NamedTextColor.WHITE),
                MessageManager.parseComponent("<white>Quantity"));
        assertEquals(Component.text("Sell All", NamedTextColor.WHITE),
                MessageManager.parseComponent("<white>Sell All"));
        assertEquals(Component.text("Return", NamedTextColor.GRAY),
                MessageManager.parseComponent("<gray>Return"));
    }

    @Test public void dialogButtonsRenderHexColorsWithoutAProvider() {
        assertEquals(Component.text("Buy", TextColor.color(0xaeffc1)),
                MessageManager.parseComponent("<#aeffc1>Buy"));
        assertEquals(Component.text("Sell", TextColor.color(0xffa0b1)),
                MessageManager.parseComponent("<#ffa0b1>Sell"));
    }

    @Test public void miniMessageSupportsBothLegacyColorPrefixes() {
        for (String input : new String[] {"&6Price: <white>10", "\u00a76Price: <white>10"}) {
            assertEquals("&6Price: &f10", LegacyComponentSerializer.legacyAmpersand()
                    .serialize(MessageManager.parseComponent(input)));
        }
    }

    @Test public void legacyOnlyFormattingAndRawGlyphsArePreserved() {
        String input = "&lBold &cRed &f\ue100";
        assertEquals(LegacyComponentSerializer.legacyAmpersand().deserialize(input),
                MessageManager.parseComponent(input));
    }

    @Test public void unknownCommandArgumentsAndLiteralAnglesRemainVisible() {
        assertEquals("Usage: /shopadmin add <price>", plain("&7Usage: /shopadmin add <price>"));
        assertEquals("Usage: <price>", plain("<gray>Usage: <price>"));
        assertEquals("1 < 2", plain("1 < 2"));
        assertEquals("", plain(null));
    }

    @Test public void ampersandHexColorsRenderInMenusAndLore() {
        Component expected = Component.text("Mycelia", TextColor.color(0x8E5FB0));
        assertEquals(expected, MessageManager.parseComponent("&#8E5FB0Mycelia"));
        assertEquals(expected, MessageManager.parseComponent("§x§8§E§5§F§B§0Mycelia"));
        assertEquals(Component.text("Buy ", TextColor.color(0xF2B94A)).append(Component.text("now", TextColor.color(0xE9E2EC))),
                MessageManager.parseComponent("&#F2B94ABuy <#E9E2EC>now"));
    }

    @Test public void chatStringsCarryHexColorsInLegacyForm() {
        assertEquals("&x&8&E&5&F&B&0Shop &7done", MessageManager.expandHex("&#8E5FB0Shop &7done"));
        assertEquals("&#8E5FB0Shop", MessageManager.shortenHex("&x&8&E&5&F&B&0Shop"));
        assertEquals("&6No hex here", MessageManager.expandHex("&6No hex here"));
        assertEquals("§f§x§F§2§B§9§4§A 1.23 coins", MessageManager.toLegacy("&f&#F2B94A 1.23 coins"));
    }

    private static String plain(String text) {
        return PlainTextComponentSerializer.plainText().serialize(MessageManager.parseComponent(text));
    }
}

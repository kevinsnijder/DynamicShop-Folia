package org.minecraftsmp.dynamicshop.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.minecraftsmp.dynamicshop.managers.MessageManager;

/** Turns in-game text into text a browser can show. */
public final class WebText {
    private static final Pattern GLYPHS =
            Pattern.compile("[\\x{E000}-\\x{F8FF}\\x{F0000}-\\x{FFFFD}\\x{100000}-\\x{10FFFD}]+");
    private static final Pattern SPACES = Pattern.compile("\\s{2,}");

    private WebText() {}

    /** Removes color codes, MiniMessage tags and resource pack glyphs. */
    public static String plain(String text) {
        if (text == null) return "";
        return withoutGlyphs(
                PlainTextComponentSerializer.plainText().serialize(MessageManager.parseComponent(text)));
    }

    /** Removes private-use characters, which resource packs draw as icons and browsers show as boxes. */
    public static String withoutGlyphs(String text) {
        return replaceGlyphs(text, "");
    }

    /** Replaces each run of resource pack glyphs with the given text and tidies the spaces. */
    public static String replaceGlyphs(String text, String replacement) {
        if (text == null) return "";
        String replaced = GLYPHS.matcher(text).replaceAll(Matcher.quoteReplacement(replacement));
        return SPACES.matcher(replaced).replaceAll(" ").trim();
    }
}

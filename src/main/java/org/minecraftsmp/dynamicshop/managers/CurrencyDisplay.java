package org.minecraftsmp.dynamicshop.managers;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Shared display templates; these never change the currency used for payments. */
public final class CurrencyDisplay {
    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:[.,][0-9]+)*");

    private CurrencyDisplay() {}

    public static boolean validFormat(String format) {
        return format != null && format.length() <= 256;
    }

    /** A symbol/name alone is shorthand for a prefix, without modifying the saved config. */
    public static String template(String configured) {
        if (!validFormat(configured) || configured.isBlank()) return "";
        return configured.contains("<money>") ? configured : configured.stripTrailing() + " <money>";
    }

    public static String format(String template, double amount, String singular, String plural) {
        return template(template).replace("<money>", String.format(Locale.US, "%,.2f", amount))
                .replace("<currency>", Math.abs(amount) == 1.0 ? singular : plural)
                .replace("<currency_singular>", singular)
                .replace("<currency_plural>", plural);
    }

    /** A browser can inherit a provider's prefix/suffix without receiving Minecraft markup. */
    public static Map<String, String> websiteSettings(
            String override, String singular, String plural, String one, String two) {
        String format = template(override);
        return Map.of(
                "format", format.isEmpty() ? providerTemplate(two) : plain(format),
                "singularFormat", format.isEmpty() ? providerTemplate(one) : plain(format),
                "singularName", plain(singular), "pluralName", plain(plural));
    }

    private static String providerTemplate(String formatted) {
        String text = plain(formatted);
        var number = NUMBER.matcher(text);
        if (!number.find()) return "$<money>";
        return text.substring(0, number.start()) + "<money>" + text.substring(number.end());
    }

    private static String plain(String text) {
        // Protect our template tokens while removing Minecraft color and MiniMessage tags.
        String protectedText = text.replace("<money>", "DS_MONEY_TOKEN")
                .replace("<currency>", "DS_CURRENCY_TOKEN")
                .replace("<currency_singular>", "DS_SINGULAR_TOKEN")
                .replace("<currency_plural>", "DS_PLURAL_TOKEN");
        return PlainTextComponentSerializer.plainText()
                .serialize(MessageManager.parseComponent(protectedText))
                .replace("DS_MONEY_TOKEN", "<money>")
                .replace("DS_CURRENCY_TOKEN", "<currency>")
                .replace("DS_SINGULAR_TOKEN", "<currency_singular>")
                .replace("DS_PLURAL_TOKEN", "<currency_plural>");
    }
}

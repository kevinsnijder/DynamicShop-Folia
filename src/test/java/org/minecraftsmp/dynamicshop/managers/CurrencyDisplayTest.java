package org.minecraftsmp.dynamicshop.managers;

import net.milkbowl.vault.economy.Economy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class CurrencyDisplayTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();

    @Test public void configuredCoinOverridesAVaultProviderReturningDollars() throws Exception {
        var plugin = PluginTestFixture.plugin(directory.getRoot());
        var manager = new MultiCurrencyEconomyManager(plugin);
        Economy provider = PluginTestFixture.proxy(Economy.class, (object, method, args) -> {
            if (method.getName().equals("format")) return "$" + args[0];
            if (method.getName().startsWith("currencyName")) return "$";
            throw new AssertionError("Currency display must not touch balances");
        });
        PluginTestFixture.set(manager, MultiCurrencyEconomyManager.class, "vaultEconomy", provider);
        assertEquals("$75.51", manager.format(75.51));
        plugin.getConfig().set("economy.currency_format", "🪙");
        assertEquals("🪙 75.51", manager.format(75.51));
        assertEquals("🪙 <money>", manager.getWebsiteCurrencySettings().get("format"));
        plugin.getConfig().set("economy.currency_format", "<money> Euro");
        assertEquals("400.00 Euro", manager.format(400));
        assertEquals("<money> Euro", manager.getWebsiteCurrencySettings().get("format"));
    }

    @Test public void vaultCoinFormatIsPreservedWithoutAnOverride() throws Exception {
        var plugin = PluginTestFixture.plugin(directory.getRoot());
        var manager = new MultiCurrencyEconomyManager(plugin);
        var calls = new AtomicInteger();
        Economy provider = PluginTestFixture.proxy(Economy.class, (object, method, args) -> {
            if (method.getName().equals("format")) {
                calls.incrementAndGet();
                return "🪙 " + String.format(java.util.Locale.US, "%.2f", args[0]);
            }
            if (method.getName().startsWith("currencyName")) return null; // LiteEco behavior
            throw new AssertionError("Formatting must not touch balances: " + method.getName());
        });
        PluginTestFixture.set(manager, MultiCurrencyEconomyManager.class, "vaultEconomy", provider);
        assertEquals("🪙 75.51", manager.format(75.51));
        assertEquals("🪙 37.56", manager.format(37.56, "default"));
        assertEquals("🪙 <money>", manager.getWebsiteCurrencySettings().get("format"));
        assertEquals(4, calls.get());
    }

    @Test public void overrideSupportsBothCoinPositionsNamesAndConfigChanges() throws Exception {
        var plugin = PluginTestFixture.plugin(directory.getRoot());
        var manager = new MultiCurrencyEconomyManager(plugin);
        plugin.getConfig().set("economy.currency_format", "🪙");
        assertEquals("🪙 75.51", manager.format(75.51));
        assertEquals("🪙 <money>", manager.getWebsiteCurrencySettings().get("format"));
        assertEquals("🪙", plugin.getConfig().getString("economy.currency_format"));
        plugin.getConfig().set("economy.currency_format", "🪙 <money>");
        assertEquals("🪙 1,234.56", manager.format(1234.56));
        plugin.getConfig().set("economy.currency_format", "<money> 🪙");
        assertEquals("37.56 🪙", manager.format(37.56));
        plugin.getConfig().set("economy.currency_format", "<money> <currency>");
        plugin.getConfig().set("economy.currency_singular_name", "token");
        plugin.getConfig().set("economy.currency_plural_name", "tokens");
        assertEquals("1.00 token", manager.format(1));
        assertEquals("2.00 tokens", manager.format(2));
        assertEquals("0.00 tokens", manager.format(0));
        assertEquals("N/A", manager.format(Double.NaN));
        assertEquals("N/A", manager.format(Double.POSITIVE_INFINITY));
    }

    @Test public void websitePreservesProviderSingularPluralAndSuffixes() {
        var settings = CurrencyDisplay.websiteSettings("", "coin", "coins", "1.00 coin", "2.00 coins");
        assertEquals("<money> coin", settings.get("singularFormat"));
        assertEquals("<money> coins", settings.get("format"));
        assertEquals("<money> 🪙", CurrencyDisplay.websiteSettings("", "", "", "1,00 🪙", "2,00 🪙").get("format"));
    }

    @Test public void websiteRemovesMinecraftMarkupButKeepsTemplateTokens() {
        var settings = CurrencyDisplay.websiteSettings("<gold>🪙 <money> <currency>", "coin", "coins", "", "");
        assertEquals("🪙 <money> <currency>", settings.get("format"));
        assertEquals("🪙 <money>", CurrencyDisplay.websiteSettings("", "coin", "coins", "§61.00", "§6🪙 2.00").get("format"));
    }

    @Test public void invalidTemplateFallsBackToTheProvider() throws Exception {
        var plugin = PluginTestFixture.plugin(directory.getRoot());
        var manager = new MultiCurrencyEconomyManager(plugin);
        plugin.getConfig().set("economy.currency_format", "a".repeat(257));
        assertEquals("$2.00", manager.format(2));
        assertTrue(CurrencyDisplay.validFormat("🪙"));
        assertFalse(CurrencyDisplay.validFormat("a".repeat(257) + "<money>"));
        assertTrue(CurrencyDisplay.validFormat(""));
    }

    @Test public void oldMessagesDoNotPrependDollarsToFormattedCoins() throws Exception {
        var plugin = PluginTestFixture.plugin(directory.getRoot());
        var messages = new org.bukkit.configuration.file.YamlConfiguration();
        messages.set("messages.playershop-purchased", "&aPurchased for ${price}");
        messages.set("messages.shop-lore-buy-price", "&aBuy: {price}");
        PluginTestFixture.set(plugin.getMessageManager(), MessageManager.class, "messagesConfig", messages);
        var price = Map.of("price", "🪙 75.51");
        assertEquals("§aPurchased for 🪙 75.51", plugin.getMessageManager().getMessage("playershop-purchased", price));
        assertEquals("§aBuy: 🪙 75.51", plugin.getMessageManager().getMessage("shop-lore-buy-price", price));
    }
}

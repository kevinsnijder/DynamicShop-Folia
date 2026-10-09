package org.minecraftsmp.dynamicshop.web;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import static org.junit.Assert.*;

public class WebCurrencyConfigTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();

    @Test public void currencySettingsAreValidatedBeforeAnyWrites() throws Exception {
        var plugin = PluginTestFixture.plugin(directory.getRoot());
        var web = new WebServer(plugin);
        var update = WebServer.class.getDeclaredMethod("applyCurrencyConfig", Map.class);
        update.setAccessible(true);
        update.invoke(web, Map.of("currencyFormat", "🪙 <money>", "currencySingularName", "coin", "currencyPluralName", "coins"));
        assertEquals("🪙 <money>", plugin.getConfig().getString("economy.currency_format"));
        assertEquals("coin", plugin.getConfig().getString("economy.currency_singular_name"));
        for (var invalid : new Object[] {42, "a".repeat(257) + "<money>"}) {
            try {
                update.invoke(web, Map.of("currencyFormat", invalid, "currencySingularName", "changed"));
                fail("Invalid format accepted");
            } catch (InvocationTargetException e) {
                assertTrue(e.getCause() instanceof IllegalArgumentException);
            }
            assertEquals("🪙 <money>", plugin.getConfig().getString("economy.currency_format"));
            assertEquals("coin", plugin.getConfig().getString("economy.currency_singular_name"));
        }
        update.invoke(web, Map.of("currencyFormat", "🪙"));
        assertEquals("🪙", plugin.getConfig().getString("economy.currency_format"));
        update.invoke(web, Map.of("currencyFormat", ""));
        assertEquals("", plugin.getConfig().getString("economy.currency_format"));
    }
}

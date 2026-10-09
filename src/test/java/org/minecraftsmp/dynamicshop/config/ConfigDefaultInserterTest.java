package org.minecraftsmp.dynamicshop.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ConfigDefaultInserterTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();

    private YamlConfiguration defaults() throws Exception {
        var defaults = new YamlConfiguration();
        defaults.loadFromString("economy:\n  sell_tax_percent: 30\n  currency_format: ''\n  currency_singular_name: ''\n  currency_plural_name: ''\ngui:\n  shop_menu_size: 54\nitems:\n  DIAMOND:\n    base: 999\nspecial_items:\n  example:\n    price: 99\n");
        return defaults;
    }

    private Path write(String source) throws Exception {
        Path file = directory.newFile().toPath();
        Files.writeString(file, source, StandardCharsets.UTF_8);
        return file;
    }

    private YamlConfiguration read(Path file) throws Exception {
        var result = new YamlConfiguration();
        result.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        return result;
    }

    @Test public void addsLinesInTheirSectionWithoutChangingSettingsCommentsOrQuotes() throws Exception {
        String source = "# Owner's config\neconomy:\n    sell_tax_percent: 17 # keep my tax\n    currency_format: '🪙 <money>' # custom format\n    custom_setting: \"keep this\"\n\n# GUI heading stays with GUI\ngui:\n  shop_menu_size: 45\nitems:\n  DIAMOND:\n    base: 12.50\nspecial_items:\n  my_item:\n    price: 123\n# Footer\n";
        var file = write(source);
        assertEquals(2, ConfigDefaultInserter.addMissing(file, defaults()));
        String updated = Files.readString(file);
        assertTrue(updated.contains("    sell_tax_percent: 17 # keep my tax\n    currency_format: '🪙 <money>' # custom format\n    custom_setting: \"keep this\"\n"));
        assertTrue(updated.contains("    currency_singular_name: ''\n    currency_plural_name: ''\n\n# GUI heading stays with GUI\ngui:\n  shop_menu_size: 45\n"));
        assertTrue(updated.endsWith("items:\n  DIAMOND:\n    base: 12.50\nspecial_items:\n  my_item:\n    price: 123\n# Footer\n"));
        assertEquals(17, read(file).getInt("economy.sell_tax_percent"));
        assertEquals(12.5, read(file).getDouble("items.DIAMOND.base"), 0);
        assertFalse(read(file).contains("special_items.example"));
        assertEquals(0, ConfigDefaultInserter.addMissing(file, defaults()));
        assertEquals(updated, Files.readString(file));
    }

    @Test public void retainsBomCrLfAndExplicitNulls() throws Exception {
        var file = write("\uFEFF# Windows file\r\neconomy:\r\n  sell_tax_percent: 18\r\n  currency_format: null # leave unset\r\ngui:\r\n  shop_menu_size: 45\r\n");
        assertEquals(2, ConfigDefaultInserter.addMissing(file, defaults()));
        String updated = Files.readString(file);
        assertTrue(updated.startsWith("\uFEFF# Windows file\r\n"));
        assertTrue(updated.contains("  currency_format: null # leave unset\r\n"));
        assertFalse(updated.replace("\r\n", "").contains("\n"));
    }

    @Test public void nestedInsertionsKeepMultilineStringsAndSurroundingSections() throws Exception {
        var defaults = new YamlConfiguration();
        defaults.loadFromString("section:\n  nested:\n    missing: 'new'\n  sibling: 7\nafter: 99\n");
        String source = "section:\n  nested:\n    note: |-\n      text\n      # This is scalar content\n\n# Next section\nafter: 42\n";
        var file = write(source);
        assertEquals(2, ConfigDefaultInserter.addMissing(file, defaults));
        assertEquals("text\n# This is scalar content", read(file).getString("section.nested.note"));
        assertEquals("new", read(file).getString("section.nested.missing"));
        assertEquals(7, read(file).getInt("section.sibling"));
        assertEquals(42, read(file).getInt("after"));
        assertTrue(Files.readString(file).contains("# Next section\nafter: 42\n"));
    }

    @Test public void missingSectionsAreAddedWithoutPopulatingShopItems() throws Exception {
        var file = write("# Preserve me\ncustom: true");
        assertEquals(5, ConfigDefaultInserter.addMissing(file, defaults()));
        String updated = Files.readString(file);
        assertTrue(updated.startsWith("# Preserve me\ncustom: true\n"));
        assertTrue(read(file).getBoolean("custom"));
        assertEquals(30, read(file).getInt("economy.sell_tax_percent"));
        assertFalse(read(file).contains("items"));
        assertFalse(read(file).contains("special_items"));
    }

    @Test public void inlineMappingsKeepTheirExistingValuesAndComments() throws Exception {
        var file = write("economy: {sell_tax_percent: 21, currency_format: '🪙 <money>'} # inline comment\ngui: {shop_menu_size: 45}\n");
        assertEquals(2, ConfigDefaultInserter.addMissing(file, defaults()));
        String updated = Files.readString(file);
        assertTrue(updated.startsWith("economy: {sell_tax_percent: 21, currency_format: '🪙 <money>'"));
        assertTrue(updated.contains("} # inline comment\ngui: {shop_menu_size: 45}\n"));
        assertEquals(21, read(file).getInt("economy.sell_tax_percent"));
        assertEquals("", read(file).getString("economy.currency_plural_name"));
        assertEquals(0, ConfigDefaultInserter.addMissing(file, defaults()));
    }

    @Test public void invalidYamlIsLeftUntouched() throws Exception {
        String original = "economy: [broken\n";
        var file = write(original);
        try {
            ConfigDefaultInserter.addMissing(file, defaults());
            fail("Invalid YAML should not be modified");
        } catch (Exception expected) {
            assertEquals(original, Files.readString(file));
        }
    }
}

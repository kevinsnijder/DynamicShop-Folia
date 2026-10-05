package org.minecraftsmp.dynamicshop.web;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Regression for Maven replacing JavaScript's ${id} with the artifact coordinates. */
public class WebResourcePackagingTest {
    private String resource(String name) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(name, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void packagedWebsitePreservesContentRevisionsAndJavaScript() throws Exception {
        String descriptor = resource("plugin.yml");
        String version =
                descriptor
                        .lines()
                        .filter(s -> s.startsWith("version:"))
                        .findFirst()
                        .orElseThrow()
                        .substring("version:".length())
                        .trim()
                        .replace("'", "");
        assertFalse(version.contains("@"));
        Path source = Path.of(System.getProperty("basedir", "."), "src/main/resources/web");
        for (String file : WebFileManager.FILES) {
            String expected = Files.readString(source.resolve(file));
            assertEquals(
                    "Resource filtering changed JavaScript/HTML in " + file,
                    expected,
                    resource("web/" + file));
            byte[] bytes = resource("web/" + file).getBytes(StandardCharsets.UTF_8);
            assertEquals(
                    "Content marker differs from actual packaged contents in " + file,
                    WebFileManager.contentRevision(bytes),
                    WebFileManager.version(bytes));
            assertFalse(expected.contains("@project.version@"));
        }
        assertTrue(resource("web/app.js").contains("id=\"${id}\""));
        assertFalse(resource("web/app.js").contains("org.minecraftsmp:DynamicShop:jar:"));
    }
}

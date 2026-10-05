package org.minecraftsmp.dynamicshop.web;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class WebFileManagerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private InputStream resource(String name) {
        return new ByteArrayInputStream(
                ("// DS-WEB-VERSION: 3.0\nnew " + name).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void preservesOldFilesUntilExplicitUpdateThenBacksThemUp() throws Exception {
        Path data = temp.getRoot().toPath(), web = data.resolve("web");
        Files.createDirectories(web);
        Files.writeString(
                web.resolve("index.html"), "<!-- DS-WEB-VERSION: 2.6.7 -->\ncustom website");
        Files.writeString(data.resolve("website-appearance.json"), "saved preferences");
        var manager = new WebFileManager(data, this::resource);
        manager.installMissing();
        assertTrue(Files.readString(web.resolve("index.html")).contains("custom website"));
        assertEquals(true, manager.status().get("needsUpdate"));
        var result = manager.update();
        assertEquals(false, manager.status().get("needsUpdate"));
        assertTrue(
                Files.readString(data.resolve((String) result.get("backup")).resolve("index.html"))
                        .contains("custom website"));
        assertEquals(
                "saved preferences", Files.readString(data.resolve("website-appearance.json")));
        assertFalse(((String) result.get("backup")).startsWith("web/"));
    }

    @Test
    public void missingBundledResourceCannotPartiallyReplaceWebsite() throws Exception {
        Path data = temp.getRoot().toPath(), web = data.resolve("web");
        Files.createDirectories(web);
        Files.writeString(web.resolve("index.html"), "old website");
        var manager =
                new WebFileManager(data, name -> name.endsWith("items.js") ? null : resource(name));
        try {
            manager.update();
            fail();
        } catch (IOException expected) {
        }
        assertEquals("old website", Files.readString(web.resolve("index.html")));
        assertFalse(Files.exists(web.resolve("app.js")));
    }

    @Test
    public void handlesAllMarkerStyles() {
        for (String marker :
                new String[] {
                    "// DS-WEB-VERSION: 3.0",
                    "/* DS-WEB-VERSION: 3.0 */",
                    "<!-- DS-WEB-VERSION: 3.0 -->"
                })
            assertEquals("3.0", WebFileManager.version(marker.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void pluginVersionChangesDoNotRequireIdenticalWebFilesToBeUpdated() throws Exception {
        var old = manager(bundle("3.0.2"));
        old.installMissing();
        var current = manager(bundle("3.0.3"));
        assertEquals(false, current.status().get("needsUpdate"));
        assertEquals(old.status().get("version"), current.status().get("version"));
        assertTrue(Files.readString(web("index.html")).contains("3.0.2"));
    }

    @Test
    public void changedContentsAreDetectedEvenWithinTheSamePluginVersion() throws Exception {
        Map<String, byte[]> resources = bundle("3.0.3");
        manager(resources).installMissing();
        var before = manager(resources).status().get("version");
        resources.put("web/app.js", bytes("// DS-WEB-VERSION: 3.0.3\nnew functionality"));
        var current = manager(resources);
        assertEquals(List.of("app.js"), current.status().get("outdatedFiles"));
        assertFalse(before.equals(current.status().get("version")));
        var result = current.update();
        assertEquals(result.get("version"), manager(resources).status().get("version"));
        assertEquals(false, manager(resources).status().get("needsUpdate"));
    }

    @Test
    public void legacyMarkersBomAndLineEndingsMigrateWithoutAnUpdate() throws Exception {
        Map<String, byte[]> resources = bundle("3.0.2");
        manager(resources).installMissing();
        for (String file : WebFileManager.FILES) {
            String body = "body " + file + "\n";
            Files.writeString(
                    web(file),
                    "\uFEFF<!-- DS-WEB-VERSION: 3.0.2 -->\r\n" + body.replace("\n", "\r\n"));
            resources.put("web/" + file, stamped(body));
        }
        assertEquals(false, manager(resources).status().get("needsUpdate"));
    }

    @Test
    public void localEditsDoNotRequestAnUpdateUntilTheBundledRevisionChanges() throws Exception {
        Map<String, byte[]> resources = bundle("3.0.3");
        resources.put("web/app.js", stamped("original app\n"));
        manager(resources).installMissing();
        Files.writeString(web("app.js"), "custom footer\n", StandardOpenOption.APPEND);
        assertEquals(false, manager(resources).status().get("needsUpdate"));
        resources.put("web/app.js", stamped("updated app\n"));
        assertEquals(List.of("app.js"), manager(resources).status().get("outdatedFiles"));
        var result = manager(resources).update();
        Path backup = temp.getRoot().toPath().resolve((String) result.get("backup"));
        assertTrue(Files.readString(backup.resolve("app.js")).contains("custom footer"));
        assertEquals(false, manager(resources).status().get("needsUpdate"));
    }

    @Test
    public void missingFilesAreDetectedAndInstallingThemClearsTheWarning() throws Exception {
        Map<String, byte[]> resources = bundle("3.0.3");
        var manager = manager(resources);
        manager.installMissing();
        Files.delete(web("items.js"));
        assertEquals(List.of("items.js"), manager.status().get("outdatedFiles"));
        manager.installMissing();
        assertEquals(false, manager.status().get("needsUpdate"));
    }

    private Map<String, byte[]> bundle(String version) {
        Map<String, byte[]> resources = new LinkedHashMap<>();
        for (String file : WebFileManager.FILES) {
            resources.put(
                    "web/" + file,
                    bytes("// DS-WEB-VERSION: " + version + "\nbody " + file + "\n"));
        }
        return resources;
    }

    private WebFileManager manager(Map<String, byte[]> resources) {
        return new WebFileManager(
                temp.getRoot().toPath(), name -> new ByteArrayInputStream(resources.get(name)));
    }

    private Path web(String file) {
        return temp.getRoot().toPath().resolve("web").resolve(file);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] stamped(String body) {
        return bytes(
                "// DS-WEB-VERSION: " + WebFileManager.contentRevision(bytes(body)) + "\n" + body);
    }
}

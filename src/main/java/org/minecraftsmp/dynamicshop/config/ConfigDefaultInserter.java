package org.minecraftsmp.dynamicshop.config;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/** Inserts missing defaults while retaining every character of the owner's existing YAML. */
public final class ConfigDefaultInserter {
    private record Insertion(int indent, String text) {}
    private final String source;
    private final String newline;
    private final YamlConfiguration existing;
    private final TreeMap<Integer, List<Insertion>> insertions = new TreeMap<>();
    private int added;

    private ConfigDefaultInserter(String source) throws Exception {
        this.source = source;
        newline = source.contains("\r\n") ? "\r\n" : "\n";
        existing = new YamlConfiguration();
        existing.loadFromString(source);
    }

    public static int addMissing(Path file, ConfigurationSection defaults) throws Exception {
        String original = Files.readString(file, StandardCharsets.UTF_8);
        boolean bom = original.startsWith("\uFEFF");
        ConfigDefaultInserter updater = new ConfigDefaultInserter(bom ? original.substring(1) : original);
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        yaml.load(updater.source); // Reject malformed/duplicate input before considering a write.
        Node root = yaml.compose(new StringReader(updater.source));
        if (root != null && !(root instanceof MappingNode)) throw new IOException("Config root must be a mapping");
        updater.plan((MappingNode) root, defaults, "", 0);
        if (updater.added == 0) return 0;
        StringBuilder updated = new StringBuilder(updater.source);
        for (var entry : updater.insertions.descendingMap().entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(Insertion::indent).reversed());
            String text = entry.getValue().stream().map(Insertion::text).collect(java.util.stream.Collectors.joining());
            updated.insert(entry.getKey(), text);
        }
        String result = (bom ? "\uFEFF" : "") + updated;
        new YamlConfiguration().loadFromString(result); // Validate the combined document before replacement.
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".dynamicshop-config-", ".tmp");
        try {
            Files.writeString(temporary, result, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return updater.added;
    }

    private void plan(MappingNode node, ConfigurationSection defaults, String path, int fallbackIndent) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        if (node != null) for (var tuple : node.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode key) nodes.put(key.getValue(), tuple.getValueNode());
        }
        int indent = node != null && !node.getValue().isEmpty()
                ? node.getValue().getFirst().getKeyNode().getStartMark().getColumn() : fallbackIndent;
        Map<String, Object> missing = new LinkedHashMap<>();
        for (String key : defaults.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            if (fullPath.equals("items") || fullPath.equals("special_items")) continue;
            if (nodes.containsKey(key) || existing.isSet(fullPath)) {
                if (defaults.isConfigurationSection(key) && nodes.get(key) instanceof MappingNode child)
                    plan(child, defaults.getConfigurationSection(key), fullPath, indent + 2);
            } else {
                Object value = defaults.get(key);
                missing.put(key, value instanceof ConfigurationSection section ? values(section) : value);
                added += value instanceof ConfigurationSection section ? Math.max(1, section.getKeys(true).stream()
                        .filter(child -> !section.isConfigurationSection(child)).count()) : 1;
            }
        }
        if (missing.isEmpty()) return;
        boolean flow = node != null && node.getFlowStyle() == DumperOptions.FlowStyle.FLOW;
        int offset;
        String text;
        if (flow) {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
            options.setWidth(Integer.MAX_VALUE);
            String serialized = new Yaml(options).dump(missing).trim();
            offset = source.offsetByCodePoints(0, node.getEndMark().getIndex()) - 1;
            text = (node.getValue().isEmpty() ? "" : ",") + newline
                    + " ".repeat(Math.max(fallbackIndent, 2))
                    + serialized.substring(1, serialized.length() - 1) + newline
                    + " ".repeat(Math.max(0, fallbackIndent - 2));
        } else {
            offset = node == null || node.getValue().isEmpty() ? source.length()
                    : afterLine(lastContent(node.getValue().getLast().getValueNode()));
            YamlConfiguration snippet = new YamlConfiguration();
            missing.forEach(snippet::set);
            StringBuilder lines = new StringBuilder();
            if (offset > 0 && source.charAt(offset - 1) != '\n') lines.append(newline);
            for (String line : snippet.saveToString().split("\n"))
                lines.append(" ".repeat(indent)).append(line).append(newline);
            text = lines.toString();
        }
        insertions.computeIfAbsent(offset, ignored -> new ArrayList<>()).add(new Insertion(indent, text));
    }

    private static Map<String, Object> values(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        section.getValues(false).forEach((key, value) -> result.put(key,
                value instanceof ConfigurationSection child ? values(child) : value));
        return result;
    }

    private static Node lastContent(Node node) {
        if (node instanceof MappingNode map && !map.getValue().isEmpty())
            return lastContent(map.getValue().getLast().getValueNode());
        if (node instanceof SequenceNode sequence && !sequence.getValue().isEmpty())
            return lastContent(sequence.getValue().getLast());
        return node;
    }

    private int afterLine(Node node) {
        // SnakeYAML marks count Unicode code points; Java string offsets count UTF-16 units.
        int end = source.offsetByCodePoints(0, node.getEndMark().getIndex());
        if (node.getEndMark().getColumn() == 0 || end >= source.length()) return end;
        int newlineIndex = source.indexOf('\n', end);
        return newlineIndex < 0 ? source.length() : newlineIndex + 1;
    }
}

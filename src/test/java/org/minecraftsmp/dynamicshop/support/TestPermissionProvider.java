package org.minecraftsmp.dynamicshop.support;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.milkbowl.vault.permission.Permission;

/** Stateful Vault provider, including group-list membership when a direct lookup misses. */
public final class TestPermissionProvider extends Permission {
    public final Map<String, Set<String>> permissions = new HashMap<>();
    public final Map<String, Set<String>> groups = new HashMap<>();
    public boolean allowGrants = true;
    public int grants;

    private static String scope(String world) { return world == null ? "global" : world; }
    public Set<String> permissions(String world) { return permissions.computeIfAbsent(scope(world), key -> new HashSet<>()); }
    public Set<String> groups(String world) { return groups.computeIfAbsent(scope(world), key -> new HashSet<>()); }
    @Override public String getName() { return "TestPermissions"; }
    @Override public boolean isEnabled() { return true; }
    @Override public boolean hasSuperPermsCompat() { return true; }
    @Override public boolean playerHas(String world, String player, String permission) { return permissions(world).contains(permission); }
    @Override public boolean playerAdd(String world, String player, String permission) {
        grants++;
        return allowGrants && permissions(world).add(permission);
    }
    @Override public boolean playerRemove(String world, String player, String permission) { return permissions(world).remove(permission); }
    @Override public boolean playerInGroup(String world, String player, String group) { return false; }
    @Override public String[] getPlayerGroups(String world, String player) { return groups(world).toArray(String[]::new); }
    @Override public boolean playerAddGroup(String world, String player, String group) {
        grants++;
        return allowGrants && groups(world).add(group);
    }
    @Override public boolean playerRemoveGroup(String world, String player, String group) { return groups(world).remove(group); }
    @Override public String getPrimaryGroup(String world, String player) { return "default"; }
    @Override public String[] getGroups() { return new String[] {"default", "EndUpgrade1"}; }
    @Override public boolean hasGroupSupport() { return true; }
    @Override public boolean groupHas(String world, String group, String permission) { return false; }
    @Override public boolean groupAdd(String world, String group, String permission) { return false; }
    @Override public boolean groupRemove(String world, String group, String permission) { return false; }
}

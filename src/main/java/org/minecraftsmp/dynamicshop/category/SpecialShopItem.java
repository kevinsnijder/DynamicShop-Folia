package org.minecraftsmp.dynamicshop.category;

import org.bukkit.Material;

/**
 * Represents a special shop entry (permissions + server-shop items).
 */
public class SpecialShopItem {

    private final String id;
    private final String name;
    private final double price;

    // Category in the shop (must include PERMISSIONS and SERVER_SHOP in ItemCategory)
    // Mutable so server-shop items can be placed in any category (e.g., TOOLS)
    private ItemCategory category;

    // For permission items
    private final String permission;

    // For server shop items (logical identifier, e.g. "pig_spawner")
    private final String itemIdentifier;

    // Optional world scope for permission node ops (null = global)
    private String permissionWorld;

    // For group items (Vault permission group name)
    private final String groupName;
    // Optional world scope for group ops (null = global)
    private String groupWorld;

    // For command items (console command with {player} placeholder)
    private String commandOnPurchase;

    // Icon used in GUIs
    private final Material displayMaterial;

    // Required permission to purchase (optional)
    private final String requiredPermission;

    // Delivery details
    private String deliveryMethod;   // "item", "permission", "nbt"
    private Material material;       // For "item" and "nbt"
    private int amount;              // For "item"
    private String nbt;              // For "nbt" (can be null)

    // ----------------------------------------------------
    // INTERNAL CONSTRUCTOR
    // ----------------------------------------------------
    private SpecialShopItem(String id,
                            String name,
                            double price,
                            ItemCategory category,
                            String permission,
                            String itemIdentifier,
                            String groupName,
                            Material displayMaterial,
                            String requiredPermission) {

        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.permission = permission;
        this.itemIdentifier = itemIdentifier;
        this.groupName = groupName;
        this.displayMaterial = displayMaterial;
        this.requiredPermission = requiredPermission;

        this.amount = 1;
        this.deliveryMethod = null;
        this.material = null;
        this.nbt = null;
        this.commandOnPurchase = null;
        this.groupWorld = null;
        this.permissionWorld = null;
    }

    // ----------------------------------------------------
    // FACTORY: PERMISSION ITEM
    // ----------------------------------------------------
    public static SpecialShopItem forPermission(String id,
                                                String displayName,
                                                double price,
                                                String permission,
                                                String permissionWorld,
                                                Material displayMaterial,
                                                String requiredPermission) {

        SpecialShopItem item = new SpecialShopItem(
                id,
                displayName,
                price,
                ItemCategory.PERMISSIONS,
                permission,
                null,
                null,
                displayMaterial,
                requiredPermission
        );

        // Delivery for permission items is handled via PermissionsManager in purchase(),
        // but we keep this here in case we reuse giveServerShopItem for perms later.
        item.permissionWorld = (permissionWorld != null && !permissionWorld.isBlank()) ? permissionWorld : null;
        item.deliveryMethod = "permission";

        return item;
    }

    // ----------------------------------------------------
    // FACTORY: GROUP ITEM
    // ----------------------------------------------------
    public static SpecialShopItem forGroup(String id,
                                           String displayName,
                                           double price,
                                           String groupName,
                                           String groupWorld,
                                           Material displayMaterial,
                                           String requiredPermission) {

        SpecialShopItem item = new SpecialShopItem(
                id,
                displayName,
                price,
                ItemCategory.PERMISSIONS,
                null,
                null,
                groupName,
                displayMaterial,
                requiredPermission
        );
        item.groupWorld = (groupWorld != null && !groupWorld.isBlank()) ? groupWorld : null;
        item.deliveryMethod = "group";

        return item;
    }

    // ----------------------------------------------------
    // FACTORY: COMMAND ITEM
    // ----------------------------------------------------
    public static SpecialShopItem forCommand(String id,
                                             String displayName,
                                             double price,
                                             String command,
                                             Material displayMaterial,
                                             String requiredPermission) {

        SpecialShopItem item = new SpecialShopItem(
                id,
                displayName,
                price,
                ItemCategory.PERMISSIONS,
                null,
                null,
                null,
                displayMaterial,
                requiredPermission
        );
        item.commandOnPurchase = command;
        item.deliveryMethod = "command";
        return item;
    }

    // ----------------------------------------------------
    // FACTORY: SERVER SHOP ITEM
    // ----------------------------------------------------
    public static SpecialShopItem forServerShop(String id,
                                                String displayName,
                                                double price,
                                                String identifier,
                                                Material displayMaterial,
                                                String requiredPermission) {

        SpecialShopItem item = new SpecialShopItem(
                id,
                displayName,
                price,
                ItemCategory.SERVER_SHOP,
                null,
                identifier,
                null,
                displayMaterial,
                requiredPermission
        );

        // By default, server shop items give a simple material stack of
        // the display material, 1 item. This can be overridden via config
        // using delivery_method/material/amount/nbt.
        item.deliveryMethod = "item";
        item.material = displayMaterial;
        item.amount = 1;

        return item;
    }

    // ----------------------------------------------------
    // GETTERS
    // ----------------------------------------------------

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public ItemCategory getCategory() {
        return category;
    }

    public String getPermission() {
        return permission;
    }

    public String getItemIdentifier() {
        return itemIdentifier;
    }

    public Material getDisplayMaterial() {
        return displayMaterial;
    }

    public String getDeliveryMethod() {
        return deliveryMethod;
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public String getNbt() {
        return nbt;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getGroupWorld() {
        return groupWorld;
    }

    public String getPermissionWorld() {
        return permissionWorld;
    }

    public String getCommandOnPurchase() {
        return commandOnPurchase;
    }

    public void setCommandOnPurchase(String command) {
        this.commandOnPurchase = command;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public boolean hasRequiredPermission() {
        return requiredPermission != null && !requiredPermission.isEmpty();
    }

    // ----------------------------------------------------
    // SETTERS (used by config / admin commands)
    // ----------------------------------------------------

    public void setDeliveryMethod(String deliveryMethod) {
        this.deliveryMethod = deliveryMethod;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public void setNbt(String nbt) {
        this.nbt = nbt;
    }

    public void setCategory(ItemCategory category) {
        this.category = category;
    }

    // ------------------------------------------------------------
    // GUI compatibility helpers
    // ------------------------------------------------------------

    public String getDisplayName() {
        return name; // GUI expects this method name
    }

    public boolean isPermissionItem() {
        return permission != null && !permission.isBlank() && !isServerShopItem();
    }

    public boolean isGroupItem() {
        return groupName != null && !groupName.isBlank();
    }

    public boolean isCommandItem() {
        return commandOnPurchase != null && !commandOnPurchase.isEmpty();
    }

    public boolean isServerShopItem() {
        return itemIdentifier != null && !itemIdentifier.isEmpty();
    }

    /** Physical custom entries can be traded; commands and other rewards remain purchases. */
    public boolean isTradeItem() {
        if (!isServerShopItem() || deliveryMethod == null) return false;
        return switch (deliveryMethod.toLowerCase(java.util.Locale.ROOT)) {
            case "itemsadder", "nexo", "oraxen", "valhallammo", "stored_item" -> true;
            default -> false;
        };
    }
}

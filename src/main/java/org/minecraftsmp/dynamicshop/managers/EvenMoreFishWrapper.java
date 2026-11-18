package org.minecraftsmp.dynamicshop.managers;

import java.lang.reflect.Method;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.minecraftsmp.dynamicshop.DynamicShop;

/**
 * Optional EvenMoreFish bridge: lets the shop buy caught fish at the worth EvenMoreFish gives
 * them. Fish are not market items, so their sales never touch stock or prices. No EvenMoreFish
 * classes are loaded unless the plugin is enabled.
 */
public final class EvenMoreFishWrapper {
    public static final String KEY_PREFIX = "EMF:";

    private static Plugin boundPlugin;
    private static Api api;
    private static boolean warned;

    private EvenMoreFishWrapper() {}

    /** One caught fish: its sale key, display name and payout per item. */
    public record Fish(String key, String name, double unitValue) {
        public double value(int amount) {
            return unitValue * amount;
        }
    }

    /** True when fish selling is switched on in config.yml and EvenMoreFish is running. */
    public static boolean enabled() {
        DynamicShop shop = DynamicShop.getInstance();
        return shop != null && shop.getConfig().getBoolean("evenmorefish.sell-fish", true) && plugin() != null;
    }

    /**
     * Returns the fish held in this stack with its per-item payout, or null when the stack is not
     * a sellable EvenMoreFish fish (fish worth nothing, such as junk, are not sellable). With
     * economy.whole-coins on, the payout is rounded down to a whole coin per fish, but a fish
     * worth anything pays at least 1 coin so small catches never get stuck unsellable.
     */
    public static Fish find(ItemStack stack) {
        if (stack == null || !enabled() || stack.getType() == Material.AIR) return null;
        try {
            Api bridge = api();
            if (bridge == null) return null;
            Object fish = bridge.fish(stack);
            if (fish == null) return null;
            double multiplier = DynamicShop.getInstance().getConfig().getDouble("evenmorefish.price-multiplier", 1.0);
            double unit = unitValue(bridge.worth(fish), multiplier);
            if (unit <= 0) return null;
            String rarity = bridge.rarityId(fish);
            String name = bridge.name(fish);
            return new Fish(KEY_PREFIX + rarity + ":" + name, name, unit);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warnOnce(e);
            return null;
        }
    }

    /**
     * Payout for one fish of the given EvenMoreFish worth, or 0 when it is not sellable. With
     * economy.whole-coins on it is rounded down, but never below 1 coin for a fish worth anything.
     */
    static double unitValue(double worth, double multiplier) {
        double value = worth * multiplier;
        if (!Double.isFinite(value) || value <= 0) return 0;
        return ConfigCacheManager.wholeCoins ? Math.max(1.0, ShopDataManager.wholeSell(value)) : value;
    }

    /** True for sale keys made by {@link #find(ItemStack)}. */
    public static boolean isFishKey(String key) {
        return key != null && key.startsWith(KEY_PREFIX);
    }

    private static Plugin plugin() {
        if (Bukkit.getServer() == null) return null;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("EvenMoreFish");
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    private static synchronized Api api() throws ReflectiveOperationException {
        Plugin plugin = plugin();
        if (plugin == null) return null;
        if (plugin != boundPlugin || api == null) {
            api = new Api(plugin);
            boundPlugin = plugin;
        }
        return api;
    }

    private static synchronized void warnOnce(Throwable error) {
        if (warned) return;
        warned = true;
        DynamicShop shop = DynamicShop.getInstance();
        if (shop != null) shop.getLogger().warning("EvenMoreFish fish could not be priced: " + error);
    }

    /** Reflection handles for the EvenMoreFish API, resolved once per plugin instance. */
    private static final class Api {
        private final Object emfApi;
        private final Method isFish;
        private final Method getFish;
        private final Method getValue;
        private final Method getRarity;
        private final Method getDisplayName;
        private Method rarityGetId;

        Api(Plugin plugin) throws ReflectiveOperationException {
            ClassLoader loader = plugin.getClass().getClassLoader();
            emfApi = plugin.getClass().getMethod("getApi").invoke(plugin);
            Class<?> apiClass = Class.forName("com.oheers.fish.api.EMFAPI", true, loader);
            isFish = apiClass.getMethod("isFish", ItemStack.class);
            getFish = apiClass.getMethod("getFish", ItemStack.class);
            Class<?> fishInterface = Class.forName("com.oheers.fish.api.fishing.items.IFish", true, loader);
            getValue = Class.forName("com.oheers.fish.selling.WorthNBT", true, loader).getMethod("getValue", fishInterface);
            Class<?> fishClass = Class.forName("com.oheers.fish.fishing.items.Fish", true, loader);
            getRarity = fishClass.getMethod("getRarity");
            getDisplayName = fishClass.getMethod("getDisplayName");
        }

        Object fish(ItemStack stack) throws ReflectiveOperationException {
            if (emfApi == null || !Boolean.TRUE.equals(isFish.invoke(emfApi, stack))) return null;
            return getFish.invoke(emfApi, stack);
        }

        double worth(Object fish) throws ReflectiveOperationException {
            Object value = getValue.invoke(null, fish);
            if (value instanceof Optional<?> optional && optional.orElse(null) instanceof Double worth) return worth;
            return 0;
        }

        String rarityId(Object fish) throws ReflectiveOperationException {
            Object rarity = getRarity.invoke(fish);
            if (rarity == null) return "unknown";
            if (rarityGetId == null) rarityGetId = rarity.getClass().getMethod("getId");
            return String.valueOf(rarityGetId.invoke(rarity));
        }

        String name(Object fish) throws ReflectiveOperationException {
            Object name = getDisplayName.invoke(fish);
            return name instanceof Component component
                    ? PlainTextComponentSerializer.plainText().serialize(component)
                    : "Fish";
        }
    }
}

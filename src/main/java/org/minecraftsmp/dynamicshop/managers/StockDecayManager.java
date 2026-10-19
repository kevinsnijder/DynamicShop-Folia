package org.minecraftsmp.dynamicshop.managers;

import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.util.FoliaScheduler;

/**
 * Lets shop stock drift back toward zero over time, so a market that players flooded
 * (or emptied) recovers instead of staying at the price limits forever.
 * Uses dynamic-pricing.stock-decay-half-life-hours; 0 disables it.
 */
public class StockDecayManager {

    private static final long PERIOD_TICKS = 5L * 60L * 20L;

    private final DynamicShop plugin;
    private FoliaScheduler.Task task;
    private long lastRunNanos;

    public StockDecayManager(DynamicShop plugin) {
        this.plugin = plugin;
    }

    /** Starts the global timer; each run decays by the real time since the previous run. */
    public void init() {
        lastRunNanos = System.nanoTime();
        task = FoliaScheduler.runGlobalTimer(plugin, this::run, PERIOD_TICKS, PERIOD_TICKS);
        if (ConfigCacheManager.stockDecayHalfLifeHours > 0) {
            plugin.getLogger().info("[StockDecay] Stock moves halfway back to 0 every "
                    + ConfigCacheManager.stockDecayHalfLifeHours + " hours.");
        }
    }

    private void run() {
        long now = System.nanoTime();
        double hours = (now - lastRunNanos) / 3.6e12;
        lastRunNanos = now;
        if (!ShopDataManager.isInitialized()) return;
        ShopDataManager.decayAllStock(hours);
    }

    /** Stops the timer. */
    public void shutdown() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
    }
}

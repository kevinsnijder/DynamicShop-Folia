package org.minecraftsmp.dynamicshop.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.transactions.Transaction;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

public class DynamicShopExpansion extends PlaceholderExpansion {

    private final DynamicShop plugin;
    // Longer names must precede their prefixes (e.g. times_bought_quantity vs times_bought).
    private static final String[] ITEM_STATS = {
        "times_bought_quantity", "times_sold_quantity", "times_bought", "times_sold",
        "timesbought", "timessold", "displayname", "net_flow", "net"
    };
    private static final String[] PLAYER_STATS = {
        "total_spent", "total_earned", "purchases", "sales"
    };

    public DynamicShopExpansion(DynamicShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "dynamicshop";
    }

    @Override
    public @NotNull String getAuthor() {
        return "selfservice0";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer p, @NotNull String id) {

        id = id.toLowerCase(Locale.ROOT);

        // Read the live stock, preserving material names such as GOLD_INGOT.
        if (id.startsWith("stock_")) {
            try {
                Material material = Material.valueOf(id.substring("stock_".length()).toUpperCase(Locale.ROOT));
                double stock = ShopDataManager.getStock(material);
                return Double.isFinite(stock) ? BigDecimal.valueOf(stock).stripTrailingZeros().toPlainString() : "0";
            } catch (IllegalArgumentException e) {
                return "0";
            }
        }

        // ------------------------------
        // Global Stats
        // ------------------------------
        switch (id) {
            case "total_transactions":
                return String.valueOf(plugin.getTransactionLogger().getTotalTransactions());
            case "items_bought":
                return String.valueOf(plugin.getTransactionLogger().getTotalItemsBought());
            case "items_sold":
                return String.valueOf(plugin.getTransactionLogger().getTotalItemsSold());
            case "total_money_exchanged":
                return String.format("%.2f", plugin.getTransactionLogger().getTotalMoneyExchanged());
            case "recent_transaction":
                Transaction last = plugin.getTransactionLogger().getMostRecentTransaction();
                return last == null ? "None"
                        : String.format(
                                "%s | %s %s %dx %s | %s",
                                last.getTimestamp(),
                                last.getPlayerName(),
                                last.getType().name(),
                                last.getAmount(),
                                last.getItem(),
                                plugin.getEconomyManager().format(last.getPrice()));

            case "most_traded_item":
                return plugin.getTransactionLogger().getMostTradedItem();
        }

        // ------------------------------
        // Player Stats
        // dynamicshop_player_<name>_<stat>
        // ------------------------------
        if (id.startsWith("player_")) {
            String request = id.substring("player_".length());
            int separator = request.lastIndexOf('_');
            if (separator < 1 || separator == request.length() - 1)
                return "invalid_format";

            for (String stat : PLAYER_STATS) {
                String suffix = "_" + stat;
                if (!request.endsWith(suffix)) continue;
                String player = request.substring(0, request.length() - suffix.length());
                if (player.isEmpty()) return "invalid_format";
                Map<String, Object> stats = plugin.getTransactionLogger().getPlayerStats(player);
                return stats == null ? "0" : String.valueOf(stats.getOrDefault(stat, "0"));
            }
            return "0";
        }

        // ------------------------------
        // Item Stats
        // dynamicshop_item_<stat>_<identifier>
        // ------------------------------
        if (id.startsWith("item_")) {
            String request = id.substring("item_".length());
            int separator = request.indexOf('_');
            if (separator < 1 || separator == request.length() - 1)
                return "invalid_format";

            for (String stat : ITEM_STATS) {
                String prefix = stat + "_";
                if (!request.startsWith(prefix)) continue;
                String identifier = request.substring(prefix.length()).toUpperCase(Locale.ROOT);
                if (identifier.isEmpty()) return "invalid_format";
                ShopDataManager.ItemStats s = ShopDataManager.getStats(identifier);
                if (s == null) return "0";
                switch (stat) {
                    case "displayname":
                        return s.displayName;
                    case "timesbought":
                    case "times_bought":
                        return String.valueOf(s.timesBought);
                    case "timessold":
                    case "times_sold":
                        return String.valueOf(s.timesSold);
                    case "times_bought_quantity":
                        return String.valueOf(s.quantityBought);
                    case "times_sold_quantity":
                        return String.valueOf(s.quantitySold);
                    case "net":
                    case "net_flow":
                        return String.valueOf(s.getNetFlow());
                }
            }
        }

        return null;
    }
}

package org.minecraftsmp.dynamicshop.commands;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.minecraftsmp.dynamicshop.DynamicShop;

public final class SellCommand implements CommandExecutor, TabCompleter {
    private final DynamicShop plugin;
    public SellCommand(DynamicShop plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use the sell menu.");
        } else if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("gui"))) {
            sender.sendMessage("§cUsage: /" + label + " [gui]");
        } else plugin.getSellMenuManager().open(player);
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 && sender.hasPermission("dynamicshop.use.sellgui")
                && "gui".startsWith(args[0].toLowerCase(java.util.Locale.ROOT)) ? List.of("gui") : List.of();
    }
}

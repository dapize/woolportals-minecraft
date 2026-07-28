package com.hakuamatata.woolportals;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WoolPortalsCommand implements CommandExecutor {

    private final WoolPortals plugin;
    private final PortalManager portalManager;

    public WoolPortalsCommand(WoolPortals plugin, PortalManager portalManager) {
        this.plugin = plugin;
        this.portalManager = portalManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list":
                handleList(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "info":
                handleInfo(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Portales de Lana ===");
        int count = 0;
        for (Portal portal : portalManager.getAllPortals()) {
            count++;
            String status = portal.isComplete() ? ChatColor.GREEN + "ENLAZADO" : ChatColor.YELLOW + "PENDIENTE";
            String owners = ChatColor.GRAY + "@" + portal.getOwnerA();
            if (portal.hasPortalB()) {
                owners += " y @" + portal.getOwnerB();
            }
            sender.sendMessage(ChatColor.AQUA + portal.getName() + " " +
                             ChatColor.WHITE + "(" + formatColor(portal.getWoolColor()) + ") " +
                             status + " " + owners);
        }
        if (count == 0) {
            sender.sendMessage(ChatColor.GRAY + "No hay portales creados aún.");
        }
        sender.sendMessage(ChatColor.GOLD + "Total: " + count + " portales");
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "WoolPortals recargado.");
    }

    private void handleInfo(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "=== Como crear un portal ===");
        sender.sendMessage(ChatColor.WHITE + "1. Construye un marco de lana 3x4 (mismo color)");
        sender.sendMessage(ChatColor.WHITE + "   WLW       W = Lana (mismo color, 10 bloques)");
        sender.sendMessage(ChatColor.WHITE + "   W W       L = Letrero (centro del borde superior)");
        sender.sendMessage(ChatColor.WHITE + "   W W       B = Boton (dentro, columna izq. o der.)");
        sender.sendMessage(ChatColor.WHITE + "   WWW");
        sender.sendMessage(ChatColor.WHITE + "2. Pon un letrero en el centro del borde superior");
        sender.sendMessage(ChatColor.WHITE + "3. Pon un boton en el interior de una columna");
        sender.sendMessage(ChatColor.WHITE + "4. En el letrero escribe:");
        sender.sendMessage(ChatColor.YELLOW + "   Linea 1: @" + (sender instanceof Player ? ((Player) sender).getName() : "tunombre"));
        sender.sendMessage(ChatColor.YELLOW + "   Linea 2: nombre-del-portal");
        sender.sendMessage(ChatColor.WHITE + "5. Construye otro portal igual en otro lado");
        sender.sendMessage(ChatColor.WHITE + "   con el mismo nombre y color de lana.");
        sender.sendMessage(ChatColor.GRAY + "Portales del mismo color + nombre se enlazan automaticamente.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== WoolPortals ===");
        sender.sendMessage(ChatColor.WHITE + "/wp list   " + ChatColor.GRAY + "- Lista todos los portales");
        sender.sendMessage(ChatColor.WHITE + "/wp info   " + ChatColor.GRAY + "- Cómo crear un portal");
        sender.sendMessage(ChatColor.WHITE + "/wp reload " + ChatColor.GRAY + "- Recargar configuración");
    }

    private String formatColor(String color) {
        if (color == null) return "?";
        String name = color.replace("_WOOL", "");
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }
}

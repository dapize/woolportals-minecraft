package com.hakuamatata.woolportals;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class WoolPortalsCommand implements CommandExecutor {

    private final PortalManager portalManager;

    public WoolPortalsCommand(WoolPortals plugin, PortalManager portalManager) {
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
                handleList(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "info":
                handleInfo(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleList(CommandSender sender, String[] args) {
        boolean showAll = args.length > 1 && args[1].equalsIgnoreCase("all");

        if (showAll && !sender.hasPermission("woolportals.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permiso para ver todos los portales.");
            return;
        }

        String playerName = (sender instanceof Player) ? ((Player) sender).getName() : null;

        List<Portal> filtered = new ArrayList<>();
        for (Portal portal : portalManager.getAllPortals()) {
            if (showAll) {
                filtered.add(portal);
            } else if (playerName != null) {
                if (playerName.equalsIgnoreCase(portal.getOwnerA()) ||
                    playerName.equalsIgnoreCase(portal.getOwnerB())) {
                    filtered.add(portal);
                }
            }
        }

        String title = showAll ? "=== Todos los Portales ===" : "=== Tus Portales ===";
        sender.sendMessage(ChatColor.GOLD + title);

        if (filtered.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + (showAll ? "No hay portales creados aun." : "No tienes portales creados."));
            sender.sendMessage(ChatColor.GOLD + "Total: 0");
            return;
        }

        int count = 0;
        for (Portal portal : filtered) {
            count++;

            String statusSymbol;
            if (portal.isComplete()) {
                statusSymbol = ChatColor.GREEN + "ON";
            } else {
                statusSymbol = ChatColor.DARK_GRAY + "OFF";
            }

            String colorName = formatColor(portal.getWoolColor());
            String priv = "";
            boolean isOwn = playerName != null && (playerName.equalsIgnoreCase(portal.getOwnerA()) || playerName.equalsIgnoreCase(portal.getOwnerB()));
            if (isOwn) {
                if (portal.getOwnerA() != null && portal.getOwnerA().equalsIgnoreCase(playerName) && portal.isPrivateA()) priv = ChatColor.GRAY + " [privado]";
                if (portal.getOwnerB() != null && portal.getOwnerB().equalsIgnoreCase(playerName) && portal.isPrivateB()) priv = ChatColor.GRAY + " [privado]";
            }

            if (showAll) {
                String owners = ChatColor.GRAY + "#" + portal.getOwnerA();
                if (portal.hasPortalB()) {
                    owners += ChatColor.GRAY + " / #" + portal.getOwnerB();
                }
                if ((portal.isPrivateA() && portal.hasPortalA()) || (portal.isPrivateB() && portal.hasPortalB())) {
                    priv = ChatColor.GRAY + " [privado]";
                }
                sender.sendMessage(ChatColor.AQUA + portal.getName() + " " +
                    ChatColor.WHITE + "(" + colorName + ") " +
                    statusSymbol + " " + owners + priv);
            } else {
                Location ownLoc = getMyLocation(portal, playerName);
                Location otherLoc = getOtherLocation(portal, playerName);

                String coords = ChatColor.GRAY + "Mundo: " + (ownLoc != null ? ownLoc.getWorld().getName() : "?") +
                    " " + formatCoords(ownLoc);

                String link = "";
                if (portal.isComplete() && otherLoc != null) {
                    link = ChatColor.LIGHT_PURPLE + " <--> " + ChatColor.GRAY +
                        "Mundo: " + otherLoc.getWorld().getName() + " " + formatCoords(otherLoc);
                }

                sender.sendMessage(ChatColor.AQUA + portal.getName() + " " +
                    ChatColor.WHITE + "(" + colorName + ") " +
                    statusSymbol + " " + coords + link + priv);
            }
        }

        sender.sendMessage(ChatColor.GOLD + "Total: " + count);
    }

    private Location getMyLocation(Portal portal, String playerName) {
        if (playerName == null) return null;
        if (playerName.equalsIgnoreCase(portal.getOwnerA())) return portal.getSignLocationA();
        if (playerName.equalsIgnoreCase(portal.getOwnerB())) return portal.getSignLocationB();
        return null;
    }

    private Location getOtherLocation(Portal portal, String playerName) {
        if (playerName == null) return null;
        if (playerName.equalsIgnoreCase(portal.getOwnerA())) return portal.getSignLocationB();
        if (playerName.equalsIgnoreCase(portal.getOwnerB())) return portal.getSignLocationA();
        return null;
    }

    private String formatCoords(Location loc) {
        if (loc == null) return "?";
        return "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("woolportals.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permiso para este comando.");
            return;
        }
        portalManager.loadPortals();
        sender.sendMessage(ChatColor.GREEN + "Portales recargados. " + portalManager.getPortalCount() + " pares cargados.");
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Como crear un portal ===");
        sender.sendMessage(ChatColor.WHITE + "1. Construye un marco de lana 3x4 (mismo color)");
        sender.sendMessage(ChatColor.WHITE + "   WLW       W = Lana (mismo color, 10 bloques)");
        sender.sendMessage(ChatColor.WHITE + "   WBW       L = Letrero (centro del borde superior)");
        sender.sendMessage(ChatColor.WHITE + "   W W       B = Boton (dentro, columna izq. o der.)");
        sender.sendMessage(ChatColor.WHITE + "   WWW");
        sender.sendMessage(ChatColor.WHITE + "2. Pon un letrero en el centro del borde superior");
        sender.sendMessage(ChatColor.WHITE + "3. Pon un boton en el interior de una columna");
        sender.sendMessage(ChatColor.WHITE + "4. En el letrero escribe:");
        sender.sendMessage(ChatColor.YELLOW + "   Linea 1: #" + (sender instanceof Player ? ((Player) sender).getName() : "tunombre"));
        sender.sendMessage(ChatColor.YELLOW + "   Linea 2: nombre-del-portal");
        sender.sendMessage(ChatColor.YELLOW + "   Linea 3: privado (opcional)");
        sender.sendMessage(ChatColor.WHITE + "5. Construye otro portal igual en otro lado");
        sender.sendMessage(ChatColor.WHITE + "   con el mismo nombre y color de lana.");
        sender.sendMessage(ChatColor.GRAY + "Portales del mismo color + nombre se enlazan automaticamente.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== WoolPortals ===");
        sender.sendMessage(ChatColor.WHITE + "/wp list   " + ChatColor.GRAY + "- Lista tus portales");
        sender.sendMessage(ChatColor.WHITE + "/wp info   " + ChatColor.GRAY + "- Como crear un portal");

        if (sender.hasPermission("woolportals.admin")) {
            sender.sendMessage(ChatColor.WHITE + "/wp list all " + ChatColor.GRAY + "- Lista todos los portales");
            sender.sendMessage(ChatColor.WHITE + "/wp reload " + ChatColor.GRAY + "- Recargar portales del disco");
        }
    }

    private String formatColor(String color) {
        if (color == null) return "?";
        String name = color.replace("_WOOL", "");
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }
}

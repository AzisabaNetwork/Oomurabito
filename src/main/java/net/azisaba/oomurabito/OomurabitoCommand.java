package net.azisaba.oomurabito;

import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class OomurabitoCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("spawn", "kill", "status", "reload");

    private final OomurabitoPlugin plugin;
    private final BossManager bossManager;

    OomurabitoCommand(OomurabitoPlugin plugin, BossManager bossManager) {
        this.plugin = plugin;
        this.bossManager = bossManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/oomurabito <spawn|kill|status|reload>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> handleSpawn(sender, args);
            case "kill" -> {
                if (bossManager.killCurrent()) {
                    sender.sendMessage(ChatColor.GREEN + "Oomurabito boss removed.");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "No Oomurabito boss is active.");
                }
            }
            case "status" -> {
                OomurabitoBoss boss = bossManager.currentBoss();
                if (boss == null) {
                    sender.sendMessage(ChatColor.YELLOW + "No Oomurabito boss is active.");
                } else {
                    sender.sendMessage(ChatColor.AQUA + boss.statusLine());
                }
            }
            case "reload" -> {
                plugin.reloadBossConfig();
                sender.sendMessage(ChatColor.GREEN + "Oomurabito config reloaded.");
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "/oomurabito <spawn|kill|status|reload>");
        }
        return true;
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        Location spawnLocation;
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Console usage: /oomurabito spawn <worldname> <x> <y> <z>");
                return;
            }
            spawnLocation = player.getLocation().clone();
        } else if (args.length == 5) {
            spawnLocation = parseLocation(sender, args);
            if (spawnLocation == null) {
                return;
            }
        } else {
            sender.sendMessage(ChatColor.YELLOW + "/oomurabito spawn [<worldname> <x> <y> <z>]");
            return;
        }

        if (!bossManager.spawn(spawnLocation)) {
            sender.sendMessage(ChatColor.RED + "An Oomurabito boss is already active.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Oomurabito boss spawned. " + ChatColor.GRAY + locationText(spawnLocation));
    }

    private Location parseLocation(CommandSender sender, String[] args) {
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World not found: " + args[1]);
            return null;
        }
        try {
            double x = Double.parseDouble(args[2]);
            double y = Double.parseDouble(args[3]);
            double z = Double.parseDouble(args[4]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "Coordinates must be numbers: /oomurabito spawn <worldname> <x> <y> <z>");
            return null;
        }
    }

    private String locationText(Location location) {
        return String.format(
                Locale.ROOT,
                "(%s %.1f %.1f %.1f)",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (!args[0].equalsIgnoreCase("spawn")) {
            return List.of();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if (sender instanceof Player player && args.length >= 3 && args.length <= 5) {
            int coordinate = switch (args.length) {
                case 3 -> player.getLocation().getBlockX();
                case 4 -> player.getLocation().getBlockY();
                case 5 -> player.getLocation().getBlockZ();
                default -> 0;
            };
            return List.of(Integer.toString(coordinate));
        }
        return List.of();
    }
}

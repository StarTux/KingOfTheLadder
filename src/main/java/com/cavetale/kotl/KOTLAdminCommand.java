package com.cavetale.kotl;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.winthier.playercache.PlayerCache;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static com.cavetale.kotl.WorldEdit.getSelection;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class KOTLAdminCommand extends AbstractCommand<KOTLPlugin> {
    protected KOTLAdminCommand(final KOTLPlugin plugin) {
        super(plugin, "kotla");
    }

    @Override
    protected void onEnable() {
        rootNode.senderCaller(this::kotla);
        rootNode.addChild("start").denyTabCompletion()
            .description("Start the game")
            .senderCaller(this::start);
        rootNode.addChild("stop").denyTabCompletion()
            .description("Stop the game")
            .senderCaller(this::stop);
        rootNode.addChild("event").arguments("true|false")
            .description("Set event state")
            .completers(CommandArgCompleter.list("true", "false"))
            .senderCaller(this::event);
        CommandNode scoreNode = rootNode.addChild("score").description("Score subcommands");
        scoreNode.addChild("reset").denyTabCompletion()
            .description("Reset scores")
            .senderCaller(this::scoreReset);
        scoreNode.addChild("add").arguments("<player> <amount>")
            .description("Manipulate score")
            .completers(PlayerCache.NAME_COMPLETER,
                        CommandArgCompleter.integer(i -> i != 0))
            .senderCaller(this::scoreAdd);
        scoreNode.addChild("reward").denyTabCompletion()
            .description("Reward scores")
            .senderCaller(this::scoreReward);
    }

    private boolean kotla(CommandSender sender, String[] args) {
        boolean r = kotla2(sender, args);
        if (!r) sender.sendMessage(plugin.getCommand(commandName).getUsage());
        return true;
    }

    private boolean kotla2(CommandSender sender, String[] args) {
        if (args.length == 0) return false;
        Player player = sender instanceof Player ? (Player) sender : null;
        String cmd = args[0];
        switch (args[0]) {
        case "reload": {
            if (args.length != 1) return false;
            plugin.loadGame();
            sender.sendMessage(ChatColor.YELLOW + "Game reloaded");
            return true;
        }
        case "save": {
            if (args.length != 1) return false;
            plugin.saveGame();
            sender.sendMessage(ChatColor.YELLOW + "Game saved");
            return true;
        }
        case "setstate": {
            if (args.length != 2) return false;
            try {
                plugin.setupState(State.valueOf(args[1].toUpperCase()));
            } catch (IllegalArgumentException iae) {
                sender.sendMessage(ChatColor.RED + "Unknown state: " + args[1]);
                return true;
            }
            sender.sendMessage(ChatColor.YELLOW + "Started state " + plugin.game.state);
            return true;
        }
        case "setarea": {
            if (args.length != 1) return false;
            if (player == null) {
                sender.sendMessage("[KOTL] Player expected");
                return true;
            }
            Rect sel = getSelection(player);
            if (sel == null) {
                sender.sendMessage(ChatColor.RED + "No selection!");
                return true;
            }
            plugin.game.world = player.getWorld().getName();
            plugin.game.area = sel;
            plugin.saveGame();
            sender.sendMessage(ChatColor.YELLOW + "Area set to " + sel);
            return true;
        }
        case "setgoal": {
            if (args.length != 1) return false;
            if (player == null) {
                sender.sendMessage("[KOTL] Player expected");
                return true;
            }
            Rect sel = getSelection(player);
            if (sel == null) {
                sender.sendMessage(ChatColor.RED + "No selection!");
                return true;
            }
            plugin.game.goal = sel;
            plugin.saveGame();
            sender.sendMessage(ChatColor.YELLOW + "Goal set to " + sel);
            return true;
        }
        case "addspawn": {
            if (args.length != 1) return false;
            if (player == null) {
                sender.sendMessage("[KOTL] Player expected");
                return true;
            }
            Rect sel = getSelection(player);
            if (sel == null) {
                sender.sendMessage(ChatColor.RED + "No selection!");
                return true;
            }
            List<Vec> vecs = sel.allVecs();
            plugin.game.spawnBlocks.addAll(vecs);
            plugin.saveGame();
            sender.sendMessage("" + ChatColor.YELLOW + vecs.size() + " spawn blocks added");
            return true;
        }
        case "clearspawn": {
            if (args.length != 1) return false;
            int count = plugin.game.spawnBlocks.size();
            plugin.game.spawnBlocks.clear();
            plugin.saveGame();
            sender.sendMessage("" + ChatColor.YELLOW + count + " spawn blocks cleared");
            return true;
        }
        case "clearwinners": {
            if (args.length != 1) return false;
            plugin.game.winners.clear();
            plugin.saveGame();
            sender.sendMessage(ChatColor.YELLOW + "Winners cleared");
            return true;
        }
        default: return false;
        }
    }

    private void start(CommandSender sender) {
        plugin.setupState(State.CLIMB);
        sender.sendMessage(text("Game started", AQUA));
    }

    private void stop(CommandSender sender) {
        plugin.setupState(State.PAUSE);
        sender.sendMessage(text("Game stopped", AQUA));
    }

    private boolean event(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 1) {
            try {
                Boolean value = Boolean.parseBoolean(args[0]);
                plugin.game.setEvent(value);
                plugin.saveGame();
                if (value) {
                    plugin.computeHighscore();
                }
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Not a boolean: " + args[0]);
            }
        }
        sender.sendMessage(plugin.game.isEvent()
                           ? text("Event mode enabled", GREEN)
                           : text("Event mode disabled", RED));
        return true;
    }

    private void scoreReset(CommandSender sender) {
        plugin.game.getScore().clear();
        plugin.saveGame();
        if (plugin.game.event) {
            plugin.computeHighscore();
        }
        sender.sendMessage(text("All scores were reset", AQUA));
    }

    private boolean scoreAdd(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        PlayerCache target = PlayerCache.require(args[0]);
        int value = CommandArgCompleter.requireInt(args[1], i -> true);
        plugin.game.addScore(target.uuid, value);
        if (plugin.game.event) {
            plugin.computeHighscore();
        }
        sender.sendMessage(text("Score of " + target.name + " is now "
                                + plugin.game.getScore(target.uuid), AQUA));
        return true;
    }

    private void scoreReward(CommandSender sender) {
        int count = plugin.rewardHighscore();
        sender.sendMessage(text(count + " highscore(s) rewarded", AQUA));
    }
}

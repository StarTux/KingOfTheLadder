package com.cavetale.kotl;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.winthier.playercache.PlayerCache;
import java.util.List;
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
        CommandNode configNode = rootNode.addChild("config");
        configNode.addChild("reload").denyTabCompletion()
            .description("Reload config")
            .senderCaller(this::reload);
        configNode.addChild("save").denyTabCompletion()
            .description("Save config")
            .senderCaller(this::save);
        configNode.addChild("setarea").denyTabCompletion()
            .description("Set game area")
            .playerCaller(this::setArea);
        configNode.addChild("setgoal").denyTabCompletion()
            .description("Set goal area")
            .playerCaller(this::setGoal);
        configNode.addChild("addspawn").denyTabCompletion()
            .description("Add selection to spawn")
            .playerCaller(this::addSpawn);
        configNode.addChild("clearspawn").denyTabCompletion()
            .description("Clear spawn")
            .playerCaller(this::clearSpawn);
        configNode.addChild("clearwinners").denyTabCompletion()
            .description("Clear winners")
            .senderCaller(this::clearWinners);
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

    private void save(CommandSender sender) {
        plugin.saveGame();
        sender.sendMessage(text("Game saved", AQUA));
    }

    private void setArea(Player player) {
        Rect sel = getSelection(player);
        if (sel == null) {
            throw new CommandWarn("No selection!");
        }
        plugin.game.world = player.getWorld().getName();
        plugin.game.area = sel;
        plugin.saveGame();
        player.sendMessage(text("Area set to " + sel, AQUA));
    }

    private void setGoal(Player player) {
        Rect sel = getSelection(player);
        if (sel == null) {
            throw new CommandWarn("No selection!");
        }
        plugin.game.goal = sel;
        plugin.saveGame();
        player.sendMessage(text("Goal set to " + sel, AQUA));
    }

    private void addSpawn(Player player) {
        Rect sel = getSelection(player);
        if (sel == null) {
            throw new CommandWarn("No selection!");
        }
        List<Vec> vecs = sel.allVecs();
        plugin.game.spawnBlocks.addAll(vecs);
        plugin.saveGame();
        player.sendMessage(text(vecs.size() + " spawn blocks added", AQUA));
    }

    private void clearSpawn(CommandSender sender) {
        int count = plugin.game.spawnBlocks.size();
        plugin.game.spawnBlocks.clear();
        plugin.saveGame();
        sender.sendMessage(text(count + " spawn blocks cleared", AQUA));
    }

    private void clearWinners(CommandSender sender) {
        plugin.game.winners.clear();
        plugin.saveGame();
        sender.sendMessage(text("Winners cleared", AQUA));
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

    private void reload(CommandSender sender) {
        plugin.loadGame();
        sender.sendMessage(text("Game reloaded", AQUA));
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

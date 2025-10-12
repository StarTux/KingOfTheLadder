package com.cavetale.kotl;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.fam.trophy.Highscore;
import com.winthier.creative.BuildWorld;
import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static com.cavetale.kotl.Games.games;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class KOTLAdminCommand extends AbstractCommand<KOTLPlugin> {
    protected KOTLAdminCommand(final KOTLPlugin plugin) {
        super(plugin, "kotla");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("start").arguments("<world>")
            .completers(CommandArgCompleter.supplyList(this::listMinigameWorldPaths))
            .description("Start a game")
            .senderCaller(this::start);
        rootNode.addChild("stop").arguments("<world>")
            .completers(CommandArgCompleter.supplyList(() -> List.copyOf(games().getWorldGameMap().keySet())))
            .description("Stop current game")
            .senderCaller(this::stop);
        rootNode.addChild("list").denyTabCompletion()
            .description("List active games")
            .senderCaller(this::list);
        rootNode.addChild("event").arguments("true|false")
            .description("Set event state")
            .completers(CommandArgCompleter.BOOLEAN)
            .senderCaller(this::event);
        rootNode.addChild("pause").arguments("true|false")
            .description("Set event state")
            .completers(CommandArgCompleter.BOOLEAN)
            .senderCaller(this::pause);
        rootNode.addChild("timeleft").arguments("<seconds>")
            .description("Set time left")
            .completers(CommandArgCompleter.integer(i -> i >= 0))
            .playerCaller(this::timeLeft);
        CommandNode configNode = rootNode.addChild("config");
        configNode.addChild("save").denyTabCompletion()
            .description("Save config")
            .senderCaller(this::save);
        configNode.addChild("reload").denyTabCompletion()
            .description("Reload config")
            .senderCaller(this::reload);
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
        plugin.saveSaveTag();
        sender.sendMessage(text("Game saved", AQUA));
    }

    private void reload(CommandSender sender) {
        plugin.loadSaveTag();
        sender.sendMessage(text("Save tag reloaded", AQUA));
    }

    public List<String> listMinigameWorldPaths() {
        List<String> result = new ArrayList<>();
        for (BuildWorld buildWorld : BuildWorld.findMinigameWorlds(MinigameMatchType.KING_OF_THE_LADDER, false)) {
            result.add(buildWorld.getPath());
        }
        return result;
    }

    private boolean start(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final String path = args[0];
        final BuildWorld buildWorld = BuildWorld.findWithPath(path);
        if (buildWorld == null) {
            throw new CommandWarn("BuildWorld not found: " + path);
        }
        if (buildWorld.getRow().parseMinigame() != MinigameMatchType.KING_OF_THE_LADDER) {
            throw new CommandWarn("Not a KOTL world: " + path);
        }
        games().startGame(buildWorld, game -> {
                sender.sendMessage(text("Starting game in " + buildWorld.getName(), GREEN));
            });
        sender.sendMessage(text("Game starting...", AQUA));
        return true;
    }

    private boolean stop(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final String worldName = args[0];
        final Game game = games().getWorldGameMap().get(worldName);
        if (game == null) {
            throw new CommandWarn("Game not found: " + worldName);
        }
        games().stopGame(game);
        sender.sendMessage(text("Game stopped: " + game.getWorld().getName(), AQUA));
        return true;
    }

    private void list(CommandSender sender) {
        if (games().getWorldGameMap().isEmpty()) {
            throw new CommandWarn("No games to show");
        }
        for (Game game : games().getWorldGameMap().values()) {
            sender.sendMessage(textOfChildren(text(game.getWorld().getName(), GRAY),
                                              space(),
                                              text("" + game.getState(), YELLOW),
                                              space(),
                                              text(game.getWorld().getPlayers() + "player", GRAY)));
        }
        sender.sendMessage(text("" + games().getWorldGameMap().size() + " games in total", YELLOW));
    }

    private boolean timeLeft(Player player, String[] args) {
        if (args.length != 1) return false;
        final int timeLeft = CommandArgCompleter.requireInt(args[0], i -> i >= 0);
        if (!games().apply(player.getWorld(), game -> game.setTimeLeft(timeLeft * 20))) {
            throw new CommandWarn("There is no game here");
        }
        player.sendMessage(text("Changed time left to " + timeLeft + " seconds", AQUA));
        return true;
    }

    private boolean event(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 1) {
            try {
                Boolean value = Boolean.parseBoolean(args[0]);
                plugin.getSaveTag().setEvent(value);
                plugin.saveSaveTag();
                if (value) {
                    plugin.computeHighscore();
                }
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Not a boolean: " + args[0]);
            }
        }
        sender.sendMessage(plugin.getSaveTag().isEvent()
                           ? text("Event mode enabled", GREEN)
                           : text("Event mode disabled", RED));
        return true;
    }

    private boolean pause(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 1) {
            try {
                Boolean value = Boolean.parseBoolean(args[0]);
                plugin.getSaveTag().setPause(value);
                plugin.saveSaveTag();
                if (value) {
                    plugin.computeHighscore();
                }
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Not a boolean: " + args[0]);
            }
        }
        sender.sendMessage(plugin.getSaveTag().isPause()
                           ? text("Paused", GREEN)
                           : text("Unpaused", RED));
        return true;
    }

    private void scoreReset(CommandSender sender) {
        plugin.getSaveTag().getScore().clear();
        plugin.saveSaveTag();
        if (plugin.getSaveTag().isEvent()) {
            plugin.computeHighscore();
        }
        sender.sendMessage(text("All scores were reset", AQUA));
    }

    private boolean scoreAdd(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        PlayerCache target = PlayerCache.require(args[0]);
        int value = CommandArgCompleter.requireInt(args[1], i -> true);
        plugin.getSaveTag().addScore(target.uuid, value);
        if (plugin.getSaveTag().isEvent()) {
            plugin.computeHighscore();
        }
        sender.sendMessage(text("Score of " + target.name + " is now "
                                + plugin.getSaveTag().getScore(target.uuid), AQUA));
        return true;
    }

    private void scoreReward(CommandSender sender) {
        int count = plugin.rewardHighscore();
        sender.sendMessage(text(count + " highscore(s) rewarded", AQUA));
        for (Component line : Highscore.rewardMoneyWithFeedback(plugin, plugin.getSaveTag().getScore(), "King of the Ladder")) {
            sender.sendMessage(line);
        }
    }
}

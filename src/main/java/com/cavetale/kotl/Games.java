package com.cavetale.kotl;

import com.cavetale.core.event.minigame.MinigameMatchType;
import com.winthier.creative.BuildWorld;
import com.winthier.creative.vote.MapVote;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import static com.cavetale.kotl.KOTLPlugin.kotlPlugin;

@Getter
public final class Games {
    private Map<String, Game> worldGameMap = new HashMap<>();
    private int gamesLoading = 0;

    public void enable() {
        Bukkit.getScheduler().runTaskTimer(kotlPlugin(), this::tick, 1L, 1L);
    }

    public void disable() {
        for (Game game : List.copyOf(worldGameMap.values())) {
            stopGame(game);
        }
        MapVote.stop(MinigameMatchType.KING_OF_THE_LADDER);
    }

    private void tick() {
        if (!worldGameMap.isEmpty() || isLoadingGames()) {
            // Games happening or loading right now
            if (MapVote.isActive(MinigameMatchType.KING_OF_THE_LADDER)) {
                MapVote.stop(MinigameMatchType.KING_OF_THE_LADDER);
            }
        } else {
            // No games.
            if (kotlPlugin().getLobbyWorld().getPlayers().size() >= 2) {
                // 2 or more players in lobby
                if (!MapVote.isActive(MinigameMatchType.KING_OF_THE_LADDER)) {
                    MapVote.start(MinigameMatchType.KING_OF_THE_LADDER, v -> {
                            v.setTitle(KOTLPlugin.DARK_TITLE);
                            v.setLobbyWorld(kotlPlugin().getLobbyWorld());
                            v.setCallback(result -> startGame(result.getBuildWorldWinner(), result.getLocalWorldCopy()));
                        });
                }
            } else {
                // 1 or less people in lobby
                if (MapVote.isActive(MinigameMatchType.KING_OF_THE_LADDER)) {
                    MapVote.stop(MinigameMatchType.KING_OF_THE_LADDER);
                }
            }
        }
        for (Game game : worldGameMap.values()) {
            game.tick();
        }
    }

    public boolean apply(World world, Consumer<Game> callback) {
        final Game game = worldGameMap.get(world.getName());
        if (game == null) {
            return false;
        }
        callback.accept(game);
        return true;
    }

    public void startGame(BuildWorld buildWorld, Consumer<Game> callback) {
        gamesLoading += 1;
        buildWorld.makeLocalCopyAsync(world -> {
                gamesLoading -= 1;
                final Game game = startGame(buildWorld, world);
                callback.accept(game);
            });
    }

    public Game startGame(BuildWorld buildWorld, World world) {
        final Game game = new Game(buildWorld, world);
        game.start();
        worldGameMap.put(world.getName(), game);
        return game;
    }

    public void stopGame(Game game) {
        worldGameMap.remove(game.getWorld().getName());
        game.stop();
    }

    public boolean isLoadingGames() {
        return gamesLoading > 0;
    }

    public static Games games() {
        return kotlPlugin().getGames();
    }
}

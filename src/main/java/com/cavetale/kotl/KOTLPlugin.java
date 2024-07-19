package com.cavetale.kotl;

import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextColor.color;
import static net.kyori.adventure.text.format.TextDecoration.*;

@Getter
public final class KOTLPlugin extends JavaPlugin {
    public static final List<String> WINNER_TITLES = List.of("Climber",
                                                              "LadderKing",
                                                              "KingOfTheLadder",
                                                              "QueenOfTheLadder",
                                                              "VineClimber",
                                                              "Ladder",
                                                              "Vine");
    public static final TextColor YELLOW2 = color(0xFFFF00);
    public static final Component TITLE = join(noSeparators(),
                                               text("King", YELLOW2),
                                               Mytems.GOLD_LADDER_TROPHY,
                                               text(tiny("of"), GRAY),
                                               text(tiny("the"), DARK_GRAY),
                                               Mytems.PARTICIPATION_LADDER_TROPHY,
                                               text("Ladder", YELLOW2));
    public static final Component DARK_TITLE = join(noSeparators(),
                                                    text("King", DARK_RED),
                                                    Mytems.GOLD_LADDER_TROPHY,
                                                    text(tiny("of"), GRAY),
                                                    text(tiny("the"), DARK_GRAY),
                                                    Mytems.PARTICIPATION_LADDER_TROPHY,
                                                    text("Ladder", DARK_RED));

    private static KOTLPlugin instance;
    // Components
    private final Games games = new Games();
    private final GameListener gameListener = new GameListener();
    private KOTLCommand kotlCommand;
    // Saving
    private SaveTag saveTag;
    // Highscores
    private List<Highscore> highscore = List.of();
    private List<Component> highscoreLines = List.of();

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        if (!NetworkServer.KING_OF_THE_LADDER.isThisServer()) {
            throw new IllegalStateException("Server = " + NetworkServer.current());
        }
        games.enable();
        gameListener.enable();
        kotlCommand = new KOTLCommand(this);
        kotlCommand.enable();
        new KOTLAdminCommand(this).enable();
        loadSaveTag();
        if (saveTag.isEvent()) computeHighscore();
    }

    @Override
    public void onDisable() {
        saveSaveTag();
        gameListener.disable();
        games.disable();
    }

    protected void loadSaveTag() {
        final File file = new File(getDataFolder(), "game.json");
        saveTag = Json.load(file, SaveTag.class, SaveTag::new);
    }

    protected void saveSaveTag() {
        getDataFolder().mkdirs();
        Json.save(new File(getDataFolder(), "game.json"), saveTag);
    }

    protected void computeHighscore() {
        highscore = Highscore.of(saveTag.getScore());
        highscoreLines = Highscore.sidebar(highscore, TrophyCategory.LADDER);
    }

    protected int rewardHighscore() {
        return Highscore.reward(saveTag.getScore(),
                                "king_of_the_ladder_event",
                                TrophyCategory.LADDER,
                                TITLE,
                                hi -> "You conquered the ladder for " + hi.score + " second" + (hi.score == 1 ? "" : "s"));
    }

    protected void onLobbyHud(PlayerHudEvent event) {
        List<Component> lines = new ArrayList<>();
        lines.add(TITLE);
        lines.addAll(highscoreLines);
        event.sidebar(PlayerHudPriority.HIGH, lines);
    }

    public World getLobbyWorld() {
        return Bukkit.getWorlds().get(0);
    }

    public static KOTLPlugin kotlPlugin() {
        return instance;
    }
}

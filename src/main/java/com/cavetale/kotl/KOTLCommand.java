package com.cavetale.kotl;

import com.cavetale.core.command.AbstractCommand;
import org.bukkit.entity.Player;
import static com.cavetale.kotl.Games.games;

public final class KOTLCommand extends AbstractCommand<KOTLPlugin> {
    public KOTLCommand(final KOTLPlugin plugin) {
        super(plugin, "kotl");
    }

    @Override
    protected void onEnable() {
        rootNode.description("KOTL command").denyTabCompletion()
            .playerCaller(this::kotl);
    }

    private void kotl(Player sender) {
        final Player player = (Player) sender;
        games().apply(player.getWorld(), game -> game.spawnPlayer(player));
    }
}

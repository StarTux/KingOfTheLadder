package com.cavetale.kotl;

import com.cavetale.core.event.block.PlayerBlockAbilityQuery;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.player.PlayerTPAEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import static com.cavetale.kotl.Games.games;
import static com.cavetale.kotl.KOTLPlugin.kotlPlugin;

public final class GameListener implements Listener {
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, kotlPlugin());
    }

    public void disable() { }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        games().apply(event.getPlayer().getWorld(), game -> game.onPlayerMove(event));
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerTeleport(PlayerTeleportEvent event) {
        games().apply(event.getPlayer().getWorld(), game -> game.onPlayerTeleport(event));
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerTPA(PlayerTPAEvent event) {
        games().apply(event.getTarget().getWorld(), game -> game.onPlayerTPA(event));
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    private void onEntityDamage(EntityDamageEvent event) {
        if (!games().apply(event.getEntity().getWorld(), game -> game.onEntityDamage(event))) {
            if (event.getEntity() instanceof Player player) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(kotlPlugin(), () -> player.teleport(kotlPlugin().getLobbyWorld().getSpawnLocation()));
            }
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    private void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        games().apply(event.getEntity().getWorld(), game -> game.onEntityDamageByEntity(event));
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onPlayerLaunchProjectile(PlayerLaunchProjectileEvent event) {
        games().apply(event.getPlayer().getWorld(), game -> game.onPlayerLaunchProjectile(event));
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onProjectileLaunch(ProjectileLaunchEvent event) {
        games().apply(event.getEntity().getWorld(), game -> game.onProjectileLaunch(event));
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onPlayerDropItem(PlayerDropItemEvent event) {
        games().apply(event.getPlayer().getWorld(), game -> game.onPlayerDropItem(event));
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerBlockAbility(PlayerBlockAbilityQuery event) {
        games().apply(event.getPlayer().getWorld(), game -> game.onPlayerBlockAbility(event));
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        games().apply(event.getEntity().getWorld(), game -> game.onEntityCombustByEntity(event));
    }

    @EventHandler
    private void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (games().apply(event.getPlayer().getWorld(), game -> game.onPlayerSpawnLocation(event))) {
            return;
        }
        for (Game game : games().getWorldGameMap().values()) {
            switch (game.getState()) {
            case INIT:
            case LOAD:
                continue;
            default:
                event.setSpawnLocation(game.randomSpawnLocation());
                return;
            }
        }
        event.setSpawnLocation(kotlPlugin().getLobbyWorld().getSpawnLocation());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!games().apply(event.getPlayer().getWorld(), game -> game.onPlayerRespawn(event))) {
            event.setRespawnLocation(kotlPlugin().getLobbyWorld().getSpawnLocation());
        }
    }

    @EventHandler
    private void onPlayerHud(PlayerHudEvent event) {
        if (games().apply(event.getPlayer().getWorld(), game -> game.onPlayerHud(event))) {
            return;
        }
        if (kotlPlugin().getSaveTag().isEvent()) {
            kotlPlugin().onLobbyHud(event);
        }
    }

    @EventHandler
    private void onPlayerRiptide(PlayerRiptideEvent event) {
        games().apply(event.getPlayer().getWorld(), game -> game.onPlayerRiptide(event));
    }
}

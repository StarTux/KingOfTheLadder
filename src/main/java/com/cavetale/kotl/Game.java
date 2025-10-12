package com.cavetale.kotl;

import com.cavetale.area.struct.AreasFile;
import com.cavetale.core.event.block.PlayerBlockAbilityQuery;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.event.minigame.MinigameMatchCompleteEvent;
import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.core.event.player.PlayerTPAEvent;
import com.cavetale.core.money.Money;
import com.cavetale.core.struct.Cuboid;
import com.cavetale.core.struct.Vec2i;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.MytemsTag;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.winthier.creative.BuildWorld;
import com.winthier.creative.file.Files;
import com.winthier.creative.review.MapReview;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import lombok.Data;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import static com.cavetale.core.font.Unicode.subscript;
import static com.cavetale.kotl.Games.games;
import static com.cavetale.kotl.KOTLPlugin.TITLE;
import static com.cavetale.kotl.KOTLPlugin.WINNER_TITLES;
import static com.cavetale.kotl.KOTLPlugin.YELLOW2;
import static com.cavetale.kotl.KOTLPlugin.kotlPlugin;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;
import static net.kyori.adventure.title.Title.Times.times;
import static net.kyori.adventure.title.Title.title;

@Data
public final class Game {
    public static final String AREAS_FILE_NAME = "KingOfTheLadder";
    public static final int COUNTDOWN_TIME = 10; // seconds
    public static final int GAME_TIME = 60 * 10; // seconds
    public static final int END_TIME = 60; // seconds
    // Constructor
    private final BuildWorld buildWorld;
    private final World world;
    // State
    private GameState state = GameState.INIT;
    private List<UUID> winners = new ArrayList<>();
    private Map<UUID, Integer> progress = new HashMap<>();
    private Map<UUID, Long> slapCooldowns = new HashMap<>();
    private Set<UUID> playerSet = new HashSet<>();
    private int timeLeft;
    // Setup
    private final List<Cuboid> gameAreas = new ArrayList<>();
    private final List<Cuboid> goalAreas = new ArrayList<>();
    private final Set<Vec3i> spawnBlocks = new HashSet<>();
    private final Random random = ThreadLocalRandom.current();
    private int spawnHeight;
    private int chunkLock;

    public boolean isInsideGameArea(Location location) {
        for (Cuboid cuboid : gameAreas) {
            if (cuboid.contains(location)) {
                return true;
            }
        }
        return false;
    }

    public boolean isInsideGoalArea(Location location) {
        for (Cuboid cuboid : goalAreas) {
            if (cuboid.contains(location)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called by Games#startGame.
     */
    protected void start() {
        if (state != GameState.INIT) {
            throw new IllegalStateException("State = " + state);
        }
        setupState(GameState.LOAD);
        loadAreas();
        final Set<Vec2i> chunksToLoad = new HashSet<>();
        for (Cuboid it : gameAreas) {
            final int az = it.az >> 4;
            final int bz = it.bz >> 4;
            final int ax = it.ax >> 4;
            final int bx = it.bx >> 4;
            for (int z = az; z <= bz; z += 1) {
                for (int x = ax; x <= bx; x += 1) {
                    chunksToLoad.add(Vec2i.of(x, z));
                }
            }
        }
        chunkLock = chunksToLoad.size() + 1;
        for (Vec2i it : chunksToLoad) {
            world.getChunkAtAsync(it.x, it.z, (Consumer<Chunk>) chunk -> {
                    chunk.addPluginChunkTicket(kotlPlugin());
                    reduceChunkLock();
                });
        }
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        world.setDifficulty(Difficulty.EASY);
        reduceChunkLock();
    }

    private void reduceChunkLock() {
        if (state != GameState.LOAD) {
            throw new IllegalStateException("State = " + state);
        }
        chunkLock -= 1;
        if (chunkLock == 0) {
            setupState(GameState.COUNTDOWN);
        }
    }

    /**
     * Called by Games#stopGame.
     */
    protected void stop() {
        for (Player player : world.getPlayers()) {
            player.eject();
            player.teleport(kotlPlugin().getLobbyWorld().getSpawnLocation());
        }
        world.removePluginChunkTickets(kotlPlugin());
        Files.deleteWorld(world);
    }

    private void loadAreas() {
        final AreasFile areasFile = AreasFile.load(world, AREAS_FILE_NAME);
        if (areasFile == null) {
            throw new IllegalStateException("No areas file: " + buildWorld.getPath() + "/" + AREAS_FILE_NAME);
        }
        for (var it : areasFile.find("game")) {
            gameAreas.add(it.toCuboid());
        }
        for (var it : areasFile.find("goal")) {
            goalAreas.add(it.toCuboid());
        }
        for (var it : areasFile.find("spawn")) {
            spawnBlocks.addAll(it.enumerate());
        }
        if (gameAreas.isEmpty()) {
            throw new IllegalStateException("No game areas: " + buildWorld.getPath());
        }
        if (goalAreas.isEmpty()) {
            throw new IllegalStateException("No goal areas: " + buildWorld.getPath());
        }
        if (spawnBlocks.isEmpty()) {
            throw new IllegalStateException("No spawn areas: " + buildWorld.getPath());
        }
        recalculateSpawnHeight();
    }

    private void log(String msg) {
        kotlPlugin().getLogger().info("[" + world.getName() + "] " + msg);
    }

    protected void onPlayerMove(PlayerMoveEvent event) {
        if (state != GameState.CLIMB) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) return;
        if (!isInsideGameArea(player.getLocation())) return;
        Location loc = player.getLocation();
        if (loc.getBlockY() > spawnHeight + 1 && playerCarriesItem(player)) {
            spawnPlayer(player);
            player.sendMessage(text("You cannot wear or hold any items in KOTL!", RED, ITALIC));
        } else if (player.isGliding()) {
            player.setGliding(false);
            spawnPlayer(player);
            player.sendMessage(text("You cannot fly in KOTL!", RED, ITALIC));
        }
        for (PotionEffect potionEffect : player.getActivePotionEffects()) {
            player.removePotionEffect(potionEffect.getType());
        }
    }

    protected void onPlayerTeleport(PlayerTeleportEvent event) {
        if (state != GameState.CLIMB) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        Location to = event.getTo();
        if (!isInsideGameArea(to)) return;
        switch (event.getCause()) {
        case ENDER_PEARL:
        case CONSUMABLE_EFFECT:
            event.setCancelled(true);
            spawnPlayer(player);
            player.sendMessage(text("You cannot ender warp in KOTL!", RED, ITALIC));
            break;
        default: break;
        }
    }

    protected void onPlayerTPA(PlayerTPAEvent event) {
        if (state != GameState.CLIMB) return;
        Player player = event.getTarget();
        Location loc = player.getLocation();
        if (isInsideGameArea(loc)) {
            event.setCancelled(true);
        }
    }

    protected void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isInsideGameArea(player.getLocation())) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Bukkit.getScheduler().runTask(kotlPlugin(), () -> spawnPlayer(player));
            }
        }
    }

    protected void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;
        if (!isInsideGameArea(target.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (state != GameState.CLIMB) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getDamager() instanceof Player damager)) {
            event.setCancelled(true);
            return;
        }
        final ItemStack hand = damager.getInventory().getItemInMainHand();
        if (hand != null && !hand.getType().isAir()) {
            event.setCancelled(true);
            return;
        }
        if (damager.getLocation().getBlockY() < spawnHeight + 2) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);
        event.setDamage(0.0);
        final int damagerScore = progress.getOrDefault(damager.getUniqueId(), 0);
        final int targetScore = progress.getOrDefault(target.getUniqueId(), 0);
        // if (damagerScore > targetScore + 20) {
        //     event.setCancelled(true);
        //     return;
        // }
        final long slapCooldown = slapCooldowns.getOrDefault(target.getUniqueId(), 0L);
        final long now = System.currentTimeMillis();
        if (slapCooldown < now && targetScore > damagerScore + 20) {
            slapCooldowns.put(target.getUniqueId(), now + 1000L);
            final var velo = new Vector(random.nextDouble() * 2.0 - 1.0, random.nextDouble() * 1.0, random.nextDouble() * 2.0 - 1.0);
            target.setVelocity(velo);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.MASTER, 1f, 1.45f);
            target.getWorld().spawnParticle(Particle.WHITE_SMOKE, target.getLocation(), 16, 0.2, 0.2, 0.2, 0.0);
            log(damager.getName() + " hit " + target.getName() + " HARD");
        }
    }

    protected void onPlayerLaunchProjectile(PlayerLaunchProjectileEvent event) {
        event.setCancelled(true);
    }

    protected void onProjectileLaunch(ProjectileLaunchEvent event) {
        event.setCancelled(true);
    }

    protected void onPlayerDropItem(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    protected void onPlayerBlockAbility(PlayerBlockAbilityQuery event) {
        if (event.getAction() == PlayerBlockAbilityQuery.Action.FLY) {
            event.setCancelled(true);
        }
    }

    protected void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        event.setCancelled(true);
    }

    protected void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        event.setSpawnLocation(randomSpawnLocation());
    }

    protected void onPlayerRespawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(randomSpawnLocation(event.getPlayer()));
    }

    protected void onPlayerHud(PlayerHudEvent event) {
        final Player player = event.getPlayer();
        final List<UUID> uuids = new ArrayList<>(progress.keySet());
        Collections.sort(uuids, (b, a) -> Integer.compare(progress.get(a), progress.get(b)));
        List<Component> lines = new ArrayList<>();
        int seconds = timeLeft / 20;
        int minutes = seconds / 60;
        lines.add(textOfChildren(text("    "), TITLE, text("    ")));
        if (state == GameState.CLIMB && isInsideGoalArea(player.getLocation())) {
            if (timeLeft % 10 < 5) {
                lines.add(text("Stay in the goal!", YELLOW2, BOLD));
            } else {
                lines.add(text("Stay in the goal!", GOLD, BOLD));
            }
        }
        lines.add(join(noSeparators(),
                       text(subscript("time left "), GRAY),
                       text(String.format("%02dm %02ds", minutes, seconds % 60), YELLOW2)));
        int playerScore = progress.getOrDefault(player.getUniqueId(), 0) / 20;
        lines.add(join(noSeparators(),
                       text(subscript("your score "), GRAY),
                       text(playerScore, YELLOW2)));
        for (int i = 0; i < 5; i += 1) {
            if (i >= uuids.size()) break;
            UUID uuid = uuids.get(i);
            Player other = Bukkit.getPlayer(uuid);
            if (other == null) continue;
            int score = progress.get(uuid) / 20;
            if (score == 0) break;
            if (isInsideGoalArea(other.getLocation())) {
                lines.add(textOfChildren(text(subscript(score), GOLD, BOLD), other.displayName()));
            } else {
                lines.add(textOfChildren(text(subscript(score), GRAY), other.displayName()));
            }
        }
        event.sidebar(PlayerHudPriority.HIGHEST, lines);
        event.bossbar(PlayerHudPriority.HIGHEST, textOfChildren(TITLE, text(": ", GRAY), text(playerScore, YELLOW2)),
                      BossBar.Color.GREEN, BossBar.Overlay.PROGRESS,
                      (float) timeLeft / (float) (GAME_TIME * 20));
    }

    protected void onPlayerRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(text("Riptide is not allowed in King of the Ladder!", RED, ITALIC));
        spawnPlayer(player);
    }

    public Location randomSpawnLocation() {
        if (world == null) return null;
        if (spawnBlocks.isEmpty()) return null;
        Vec3i spawnBlock = new ArrayList<>(spawnBlocks).get(random.nextInt(spawnBlocks.size()));
        Location location = world
            .getBlockAt(spawnBlock.x, spawnBlock.y, spawnBlock.z)
            .getLocation()
            .add(0.5, 0.0, 0.5);
        return location;
    }

    public Location randomSpawnLocation(Player player) {
        Location playerLocation = player.getLocation();
        if (spawnBlocks.isEmpty()) return null;
        Vec3i spawnBlock = new ArrayList<>(spawnBlocks).get(random.nextInt(spawnBlocks.size()));
        Location location = world
            .getBlockAt(spawnBlock.x, spawnBlock.y, spawnBlock.z)
            .getLocation()
            .add(0.5, 0.0, 0.5);
        location.setPitch(playerLocation.getPitch());
        location.setYaw(playerLocation.getYaw());
        return location;
    }

    public boolean spawnPlayer(Player player) {
        player.eject();
        Location location = randomSpawnLocation(player);
        if (location == null) return false;
        if (!player.teleport(location)) {
            return false;
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFallDistance(0);
        player.getInventory().clear();
        player.getEnderChest().clear();
        if (kotlPlugin().getSaveTag().isEvent() && !kotlPlugin().getSaveTag().getScore().containsKey(player.getUniqueId())) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
            kotlPlugin().getSaveTag().getScore().computeIfAbsent(player.getUniqueId(), u -> 0);
        }
        return true;
    }

    public boolean playerCarriesItem(Player player) {
        for (ItemStack item: new ItemStack[] {
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand(),
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
            }) {
            if (item != null && !item.getType().isAir()) {
                Mytems mytems = Mytems.forItem(item);
                if (mytems != null && (MytemsTag.WARDROBE.isTagged(mytems) || MytemsTag.WARDROBE_HAT.isTagged(mytems))) {
                    // Temporary workaround (WITCH_HAT is not in WARDROBE)
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    public void setupState(GameState newState) {
        switch (newState) {
        case COUNTDOWN: {
            for (Player player : kotlPlugin().getLobbyWorld().getPlayers()) {
                spawnPlayer(player);
            }
            timeLeft = COUNTDOWN_TIME * 20;
            buildWorld.announceMap(world);
            break;
        }
        case CLIMB: {
            for (final Player player : world.getPlayers()) {
                if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) continue;
                if (!isInsideGameArea(player.getLocation())) continue;
                spawnPlayer(player);
                player.showTitle(title(text("GO!", YELLOW2, ITALIC),
                                       text("King of the Ladder", RED),
                                       times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(3))));
                player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.MASTER, 1.0f, 1.0f);
                if (kotlPlugin().getSaveTag().isEvent()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
                    kotlPlugin().getSaveTag().getScore().computeIfAbsent(player.getUniqueId(), u -> 0);
                }
            }
            progress.clear();
            timeLeft = GAME_TIME * 20;
            recalculateSpawnHeight();
            break;
        }
        case END: {
            timeLeft = END_TIME * 20;
            MapReview.start(world, buildWorld).remindAll();
            final MinigameMatchCompleteEvent event = new MinigameMatchCompleteEvent(MinigameMatchType.KING_OF_THE_LADDER);
            for (UUID player : playerSet) {
                event.addPlayerUuid(player);
            }
            for (UUID winner : winners) {
                event.addWinnerUuid(winner);
            }
            event.callEvent();
            break;
        }
        default:
            break;
        }
        this.state = newState;
    }

    private void recalculateSpawnHeight() {
        spawnHeight = world.getMinHeight();
        for (Vec3i spawnBlock : spawnBlocks) {
            spawnHeight = Math.max(spawnHeight, spawnBlock.y);
        }
    }

    private void win(Player winner, int score) {
        winners.add(winner.getUniqueId());
        if (kotlPlugin().getSaveTag().isEvent()) {
            String cmd = "titles unlockset " + winner.getName() + " " + String.join(" ", WINNER_TITLES);
            log("Running command: " + cmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        for (Player player : world.getPlayers()) {
            if (!isInsideGameArea(player.getLocation())) continue;
            player.showTitle(title(text(winner.getName(), YELLOW2),
                                   text("Wins King of the Ladder!", YELLOW2),
                                   times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(3))));
            player.sendMessage(join(noSeparators(),
                                    newline(),
                                    text(winner.getName() + " wins King of the Ladder!", YELLOW2),
                                    newline()));
            player.playSound(player.getEyeLocation(), Sound.ENTITY_WITHER_DEATH, SoundCategory.MASTER, 0.5f, 1.0f);
        }
        Firework firework = world.spawn(winner.getLocation().add(0, 3.0, 0.0), Firework.class, (fw) -> {
                FireworkMeta meta = fw.getFireworkMeta();
                for (int i = 0; i <= 5; i += 1) {
                    Vector cv = new Vector(Math.random(), Math.random(), Math.random()).normalize();
                    meta.addEffect(FireworkEffect.builder()
                                   .with(FireworkEffect.Type.BALL)
                                   .withColor(Color.fromRGB((int) (cv.getX() * 255.0),
                                                            (int) (cv.getY() * 255.0),
                                                            (int) (cv.getZ() * 255.0)))
                                   .build());
                }
                fw.setFireworkMeta(meta);
            });
        firework.detonate();
    }

    /**
     * Called by Games:tick.
     */
    protected void tick() {
        switch (state) {
        case COUNTDOWN: tickCountdown(); break;
        case CLIMB: tickClimb(); break;
        case END: tickEnd(); break;
        default: break;
        }
    }

    private void tickCountdown() {
        if (timeLeft == 0) {
            setupState(GameState.CLIMB);
        } else if (timeLeft % 20 == 0) {
            for (Player player : world.getPlayers()) {
                player.showTitle(title(empty(), text(timeLeft / 20, GREEN)));
            }
            timeLeft -= 1;
        } else {
            timeLeft -= 1;
        }
    }

    private void tickClimb() {
        List<Player> goalPlayers = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (isInsideGoalArea(player.getLocation())) {
                goalPlayers.add(player);
            }
            if (isInsideGameArea(player.getLocation())) {
                playerSet.add(player.getUniqueId());
                if (player.isInsideVehicle()) {
                    player.leaveVehicle();
                    player.sendMessage(text("Vehicles not allowed in King of the Ladder!", RED, ITALIC));
                }
            }
        }
        if (goalPlayers.size() == 1) {
            UUID uuid = goalPlayers.get(0).getUniqueId();
            progress.compute(uuid, (u, i) -> (i != null ? i : 0) + 1);
        }
        timeLeft -= 1;
        if (timeLeft <= 0) {
            Player winner = null;
            int max = 0;
            for (Map.Entry<UUID, Integer> entry : progress.entrySet()) {
                UUID uuid = entry.getKey();
                int score = entry.getValue();
                if (kotlPlugin().getSaveTag().isEvent()) {
                    kotlPlugin().getSaveTag().addScore(uuid, score / 20);
                    Money.get().give(uuid, score * 15, kotlPlugin(), "King of the Ladder", r -> { });
                    kotlPlugin().computeHighscore();
                }
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                if (score > max) {
                    winner = player;
                    max = score;
                }
            }
            if (winner != null) win(winner, max);
            setupState(GameState.END);
        }
    }

    private void tickEnd() {
        if (timeLeft <= 0) {
            MapReview.stop(world);
            games().stopGame(this);
        } else {
            timeLeft -= 1;
        }
    }
}

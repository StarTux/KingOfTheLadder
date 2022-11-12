package com.cavetale.kotl;

import com.cavetale.core.event.block.PlayerBlockAbilityQuery;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.event.player.PlayerTPAEvent;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.MytemsTag;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextColor.color;
import static net.kyori.adventure.text.format.TextDecoration.*;
import static net.kyori.adventure.title.Title.Times.times;
import static net.kyori.adventure.title.Title.title;

public final class KOTLPlugin extends JavaPlugin implements Listener {
    public static final int GAME_TIME = 300;
    private static final List<String> WINNER_TITLES = List.of("Climber",
                                                              "LadderKing",
                                                              "KingOfTheLadder",
                                                              "QueenOfTheLadder",
                                                              "VineClimber",
                                                              "Ladder",
                                                              "Vine");
    protected Game game;
    private transient int spawnHeight;
    private BukkitTask task;
    private List<Highscore> highscore = List.of();
    private List<Component> highscoreLines = List.of();
    public static final TextColor YELLOW2 = color(0xFFFF00);
    public static final Component TITLE = join(noSeparators(),
                                               text("King", YELLOW2),
                                               Mytems.GOLD_LADDER_TROPHY,
                                               text(tiny("of"), GRAY),
                                               text(tiny("the"), DARK_GRAY),
                                               Mytems.PARTICIPATION_LADDER_TROPHY,
                                               text("Ladder", YELLOW2));

    @Override
    public void onEnable() {
        getCommand("kotl").setExecutor((a, b, c, d) -> onGameCommand(a, d));
        new KOTLAdminCommand(this).enable();
        getServer().getPluginManager().registerEvents(this, this);
        loadGame();
        if (game.state == State.CLIMB) startTask();
        if (game.event) computeHighscore();
    }

    @Override
    public void onDisable() {
        saveGame();
        stopTask();
    }

    void startTask() {
        stopTask();
        task = Bukkit.getScheduler().runTaskTimer(this, this::run, 1L, 1L);
    }

    void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean onGameCommand(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;
        boolean result = spawnPlayer(player);
        return true;
    }

    Location randomSpawnLocation() {
        World world = getServer().getWorld(game.world);
        if (world == null) return null;
        if (game.spawnBlocks.isEmpty()) return null;
        Vec3i spawnBlock = new ArrayList<>(game.spawnBlocks).get(ThreadLocalRandom.current().nextInt(game.spawnBlocks.size()));
        Location location = world
            .getBlockAt(spawnBlock.x, spawnBlock.y, spawnBlock.z)
            .getLocation()
            .add(0.5, 0.0, 0.5);
        return location;
    }

    Location randomSpawnLocation(Player player) {
        World world = getServer().getWorld(game.world);
        if (world == null) return null;
        Location playerLocation = player.getLocation();
        if (game.spawnBlocks.isEmpty()) return null;
        Vec3i spawnBlock = new ArrayList<>(game.spawnBlocks).get(ThreadLocalRandom.current().nextInt(game.spawnBlocks.size()));
        Location location = world
            .getBlockAt(spawnBlock.x, spawnBlock.y, spawnBlock.z)
            .getLocation()
            .add(0.5, 0.0, 0.5);
        location.setPitch(playerLocation.getPitch());
        location.setYaw(playerLocation.getYaw());
        return location;
    }

    boolean spawnPlayer(Player player) {
        Location location = randomSpawnLocation(player);
        if (location == null) return false;
        boolean result = player.teleport(location);
        if (result && game.event && !game.score.containsKey(player.getUniqueId())) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
            game.score.computeIfAbsent(player.getUniqueId(), u -> 0);
        }
        return result;
    }

    boolean playerCarriesItem(Player player) {
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

    void setupState(State state) {
        switch (state) {
        case PAUSE:
            stopTask();
            if (game.event) {
                computeHighscore();
            }
            break;
        case CLIMB: {
            // Check if everything is set
            if (game.spawnBlocks.isEmpty()) {
                getServer().broadcast(text("[KOTL] No spawn blocks configured!", RED),
                                      "kotl.admin");
                return;
            }
            if (game.goal.equals(Rect.ZERO)) {
                getServer().broadcast(text("[KOTL] No goal configured!", RED),
                                      "kotl.admin");
                return;
            }
            if (game.area.a.equals(Vec3i.ZERO) && game.area.b.equals(Vec3i.ZERO)) {
                getServer().broadcast(text("[KOTL] No area configured!", RED),
                                      "kotl.admin");
                return;
            }
            World world = getWorld();
            if (world == null) {
                getServer().broadcast(text("[KOTL] World not found: " + game.world, RED),
                                      "kotl.admin");
                return;
            }
            for (final Player player : world.getPlayers()) {
                if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) continue;
                if (!game.area.contains(player.getLocation())) continue;
                spawnPlayer(player);
                player.showTitle(title(text("GO!", YELLOW2, ITALIC),
                                       text("King of the Ladder", RED),
                                       times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(3))));
                player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.MASTER, 1.0f, 1.0f);
                if (game.event) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
                    game.score.computeIfAbsent(player.getUniqueId(), u -> 0);
                }
            }
            game.progress.clear();
            game.timeLeft = GAME_TIME * 20;
            stopTask();
            startTask();
            recalculateSpawnHeight();
            break;
        }
        default:
            break;
        }
        game.state = state;
        saveGame();
    }

    protected void loadGame() {
        game = Json.load(new File(getDataFolder(), "game.json"), Game.class);
        if (game == null) {
            game = new Game();
            game.state = State.PAUSE;
            game.world = getServer().getWorlds().get(0).getName();
            game.area = new Rect(Vec3i.ZERO, Vec3i.ZERO);
            game.goal = new Rect(Vec3i.ZERO, Vec3i.ZERO);
            game.spawnBlocks = new HashSet<>();
            game.winners = new ArrayList<>();
            game.progress = new HashMap<>();
        }
        recalculateSpawnHeight();
    }

    protected void saveGame() {
        getDataFolder().mkdirs();
        Json.save(new File(getDataFolder(), "game.json"), game);
    }

    private void recalculateSpawnHeight() {
        spawnHeight = 0;
        for (Vec3i spawnBlock: game.spawnBlocks) {
            spawnHeight = Math.max(spawnHeight, spawnBlock.y);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (game.state != State.CLIMB) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) return;
        if (!player.getWorld().getName().equals(game.world) || !game.area.contains(player.getLocation())) return;
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

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (game.state != State.CLIMB) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        Location to = event.getTo();
        if (to.getWorld().getName().equals(game.world) && game.area.contains(to)) {
            switch (event.getCause()) {
            case ENDER_PEARL:
            case CHORUS_FRUIT:
                event.setCancelled(true);
                spawnPlayer(player);
                player.sendMessage(text("You cannot ender warp in KOTL!", RED, ITALIC));
                break;
            default: break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTPAEvent(PlayerTPAEvent event) {
        if (game.state != State.CLIMB) return;
        Player player = event.getTarget();
        Location loc = player.getLocation();
        if (loc.getWorld().getName().equals(game.world) && game.area.contains(loc)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    private void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!player.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(player.getLocation())) return;
        if (game.state != State.CLIMB) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getDamager() instanceof Player damager)) {
            event.setCancelled(true);
            return;
        }
        ItemStack hand = damager.getInventory().getItemInMainHand();
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
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onPlayerLaunchProjectile(PlayerLaunchProjectileEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(player.getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!projectile.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(projectile.getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onPlayerDropItem(PlayerDropItemEvent event) {
        if (game.state != State.CLIMB) return;
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(player.getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerBlockAbility(PlayerBlockAbilityQuery event) {
        if (game.state != State.CLIMB) return;
        if (event.getAction() == PlayerBlockAbilityQuery.Action.FLY) {
            Player player = event.getPlayer();
            if (!player.getWorld().getName().equals(game.world)) return;
            if (!game.area.contains(player.getLocation())) return;
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!entity.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(entity.getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (game.state != State.CLIMB) return;
        Location spawnLocation = event.getSpawnLocation();
        if (game.goal.contains(spawnLocation) && spawnLocation.getWorld().getName().equals(game.world)) {
            Location location = randomSpawnLocation();
            if (location != null) {
                event.setSpawnLocation(location);
            }
        }
    }

    private void win(Player winner, int score) {
        game.winners.add(winner.getUniqueId());
        if (game.event) {
            String cmd = "titles unlockset " + winner.getName() + " " + String.join(" ", WINNER_TITLES);
            getLogger().info("Running command: " + cmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        World world = getWorld();
        for (Player player : getWorld().getPlayers()) {
            if (!game.area.contains(player.getLocation())) continue;
            player.showTitle(title(text(winner.getName(), YELLOW2),
                                   text("Wins King of the Ladder!", YELLOW2),
                                   times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(3))));
            player.sendMessage(join(noSeparators(),
                                    Component.newline(),
                                    text(winner.getName() + " wins King of the Ladder!", YELLOW2),
                                    Component.newline()));
            player.playSound(player.getEyeLocation(), Sound.ENTITY_WITHER_DEATH, SoundCategory.MASTER, 0.5f, 1.0f);
        }
        Firework firework = getWorld().spawn(winner.getLocation().add(0, 3.0, 0.0), Firework.class, (fw) -> {
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(player.getLocation())) return;
        event.setRespawnLocation(randomSpawnLocation(player));
    }

    @EventHandler
    private void onPlayerHud(PlayerHudEvent event) {
        if (game == null) return;
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(player.getLocation())) return;
        if (game.state == State.CLIMB) {
            List<UUID> uuids = new ArrayList<>(game.progress.keySet());
            Collections.sort(uuids, (b, a) -> Integer.compare(game.progress.get(a), game.progress.get(b)));
            List<Component> lines = new ArrayList<>();
            int seconds = game.timeLeft / 20;
            int minutes = seconds / 60;
            lines.add(TITLE);
            lines.add(join(noSeparators(),
                           text("Time Left ", GRAY),
                           text(String.format("%02dm %02ds", minutes, seconds % 60), YELLOW2)));
            int playerScore = game.progress.getOrDefault(player.getUniqueId(), 0) / 20;
            lines.add(join(noSeparators(),
                           text("Your score ", GRAY),
                           text(playerScore, YELLOW2)));
            if (game.goal.contains(player.getLocation())) {
                lines.add(text("Stay in the goal!", YELLOW2, BOLD));
            }
            for (int i = 0; i < 5; i += 1) {
                if (i >= uuids.size()) break;
                UUID uuid = uuids.get(i);
                Player other = Bukkit.getPlayer(uuid);
                if (other == null) continue;
                int score = game.progress.get(uuid) / 20;
                if (score == 0) break;
                lines.add(join(noSeparators(),
                               text(score + " ", YELLOW2),
                               other.displayName()));
            }
            event.sidebar(PlayerHudPriority.HIGHEST, lines);
            event.bossbar(PlayerHudPriority.HIGHEST, textOfChildren(TITLE, text(" " + playerScore, YELLOW2)),
                          BossBar.Color.GREEN, BossBar.Overlay.PROGRESS,
                          (float) game.timeLeft / (float) (GAME_TIME * 20));
        } else if (game.event) {
            List<Component> lines = new ArrayList<>();
            lines.add(TITLE);
            lines.addAll(highscoreLines);
            event.sidebar(PlayerHudPriority.HIGHEST, lines);
        }
    }

    @EventHandler
    private void onPlayerRiptide(PlayerRiptideEvent event) {
        if (game.state != State.CLIMB) return;
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(player.getLocation())) return;
        player.sendMessage(text("Riptide is not allowed in King of the Ladder!", RED, ITALIC));
        spawnPlayer(player);
    }

    private World getWorld() {
        return Bukkit.getWorld(game.world);
    }

    private void run() {
        World world = getWorld();
        if (world == null) {
            setupState(State.PAUSE);
            return;
        }
        List<Player> goalPlayers = new ArrayList<>();
        for (Player player : getWorld().getPlayers()) {
            if (game.goal.contains(player.getLocation())) {
                goalPlayers.add(player);
            }
            if (game.area.contains(player.getLocation())) {
                player.setFoodLevel(20);
                player.setSaturation(20f);
                if (player.isInsideVehicle()) {
                    player.leaveVehicle();
                    player.sendMessage(text("Vehicles not allowed in King of the Ladder!", RED, ITALIC));
                }
            }
        }
        if (goalPlayers.size() == 1) {
            UUID uuid = goalPlayers.get(0).getUniqueId();
            game.progress.compute(uuid, (u, i) -> (i != null ? i : 0) + 1);
        }
        game.timeLeft -= 1;
        if (game.timeLeft <= 0) {
            Player winner = null;
            int max = 0;
            for (Map.Entry<UUID, Integer> entry : game.progress.entrySet()) {
                UUID uuid = entry.getKey();
                int score = entry.getValue();
                if (game.event) {
                    game.addScore(uuid, score / 20);
                }
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                if (score > max) {
                    winner = player;
                    max = score;
                }
            }
            if (winner != null) win(winner, max);
            setupState(State.PAUSE);
        }
    }

    protected void computeHighscore() {
        highscore = Highscore.of(game.score);
        highscoreLines = Highscore.sidebar(highscore, TrophyCategory.LADDER);
    }

    protected int rewardHighscore() {
        return Highscore.reward(game.score,
                                "king_of_the_ladder_event",
                                TrophyCategory.LADDER,
                                TITLE,
                                hi -> "You conquered the ladder for " + hi.score + " second" + (hi.score == 1 ? "" : "s"));
    }
}

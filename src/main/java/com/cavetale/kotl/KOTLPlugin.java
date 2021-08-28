package com.cavetale.kotl;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public final class KOTLPlugin extends JavaPlugin implements Listener {
    private Game game;
    private transient int spawnHeight;
    private BukkitTask task;

    // --- Java Plugin

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("kotl").setExecutor((a, b, c, d) -> onGameCommand(a, d));
        getCommand("kotla").setExecutor((a, b, c, d) -> onAdminCommand(a, d));
        getServer().getPluginManager().registerEvents(this, this);
        loadGame();
        if (game.state == State.CLIMB) startTask();
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
        spawnPlayer(player);
        return true;
    }

    public boolean onAdminCommand(CommandSender sender, String[] args) {
        if (args.length == 0) return false;
        Player player = sender instanceof Player ? (Player) sender : null;
        String cmd = args[0];
        switch (args[0]) {
        case "reload": {
            if (args.length != 1) return false;
            loadGame();
            sender.sendMessage(ChatColor.YELLOW + "Game reloaded");
            return true;
        }
        case "save": {
            if (args.length != 1) return false;
            saveGame();
            sender.sendMessage(ChatColor.YELLOW + "Game saved");
            return true;
        }
        case "setstate": {
            if (args.length != 2) return false;
            try {
                setupState(State.valueOf(args[1].toUpperCase()));
            } catch (IllegalArgumentException iae) {
                sender.sendMessage(ChatColor.RED + "Unknown state: " + args[1]);
                return true;
            }
            sender.sendMessage(ChatColor.YELLOW + "Started state " + game.state);
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
            game.world = player.getWorld().getName();
            game.area = sel;
            saveGame();
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
            game.goal = sel;
            saveGame();
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
            game.spawnBlocks.addAll(vecs);
            saveGame();
            sender.sendMessage("" + ChatColor.YELLOW + vecs.size() + " spawn blocks added");
            return true;
        }
        case "clearspawn": {
            if (args.length != 1) return false;
            int count = game.spawnBlocks.size();
            game.spawnBlocks.clear();
            saveGame();
            sender.sendMessage("" + ChatColor.YELLOW + count + " spawn blocks cleared");
            return true;
        }
        case "clearwinners": {
            if (args.length != 1) return false;
            game.winners.clear();
            saveGame();
            sender.sendMessage(ChatColor.YELLOW + "Winners cleared");
            return true;
        }
        default: return false;
        }
    }

    // --- Player Utility

    Location randomSpawnLocation() {
        World world = getServer().getWorld(game.world);
        if (world == null) return null;
        if (game.spawnBlocks.isEmpty()) return null;
        Vec spawnBlock = new ArrayList<>(game.spawnBlocks).get(ThreadLocalRandom.current().nextInt(game.spawnBlocks.size()));
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
        Vec spawnBlock = new ArrayList<>(game.spawnBlocks).get(ThreadLocalRandom.current().nextInt(game.spawnBlocks.size()));
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
        return player.teleport(location);
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
            if (item != null && item.getType() != Material.AIR) {
                return true;
            }
        }
        return false;
    }

    Rect getSelection(Player player) {
        return WorldEdit.getSelection(player);
    }

    void setupState(State state) {
        switch (state) {
        case PAUSE:
            stopTask();
            break;
        case CLIMB: {
            // Check if everything is set
            if (game.spawnBlocks.isEmpty()) {
                getServer().broadcast(ChatColor.RED + "[KOTL] No spawn blocks configured!", "kotl.admin");
                return;
            }
            if (game.goal.equals(Rect.ZERO)) {
                getServer().broadcast(ChatColor.RED + "[KOTL] No goal configured!", "kotl.admin");
                return;
            }
            if (game.area.a.equals(Vec.ZERO) && game.area.b.equals(Vec.ZERO)) {
                getServer().broadcast(ChatColor.RED + "[KOTL] No area configured!", "kotl.admin");
                return;
            }
            World world = getWorld();
            if (world == null) {
                getServer().broadcast("[KOTL] World not found: " + game.world, "kotl.admin");
                return;
            }
            for (final Player player : world.getPlayers()) {
                if (player.isOp() || player.getGameMode() == GameMode.SPECTATOR) continue;
                if (!game.area.contains(player.getLocation())) continue;
                spawnPlayer(player);
                player.sendTitle("" + ChatColor.GOLD + ChatColor.ITALIC + "GO!",
                                 "" + ChatColor.GOLD + "King of the Ladder",
                                 0, 20, 60);
                player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            game.scores.clear();
            reloadConfig();
            int time = getConfig().getInt("time", 300);
            game.timeLeft = time * 20;
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

    void loadGame() {
        Gson gson = new Gson();
        try {
            game = gson.fromJson(new FileReader(new File(getDataFolder(), "game.json")), Game.class);
        } catch (IOException fnfe) {
            game = new Game();
            game.state = State.PAUSE;
            game.world = getServer().getWorlds().get(0).getName();
            game.area = new Rect(Vec.ZERO, Vec.ZERO);
            game.goal = new Rect(Vec.ZERO, Vec.ZERO);
            game.spawnBlocks = new HashSet<>();
            game.winners = new ArrayList<>();
            game.scores = new HashMap<>();
        }
        recalculateSpawnHeight();
    }

    void recalculateSpawnHeight() {
        spawnHeight = 0;
        for (Vec spawnBlock: game.spawnBlocks) {
            spawnHeight = Math.max(spawnHeight, spawnBlock.y);
        }
    }

    void saveGame() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        getDataFolder().mkdirs();
        try {
            FileWriter fw = new FileWriter(new File(getDataFolder(), "game.json"));
            gson.toJson(game, fw);
            fw.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    // --- Event Handling

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (game.state != State.CLIMB) return;
        Player player = event.getPlayer();
        if (player.isOp() || player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) return;
        if (!player.getWorld().getName().equals(game.world) || !game.area.contains(player.getLocation())) return;
        Location loc = player.getLocation();
        if (loc.getBlockY() > spawnHeight + 1 && playerCarriesItem(player)) {
            spawnPlayer(player);
            player.sendMessage("" + ChatColor.RED + ChatColor.ITALIC + "You cannot wear or hold any items in KOTL!");
        } else if (player.isGliding()) {
            player.setGliding(false);
            spawnPlayer(player);
            player.sendMessage("" + ChatColor.RED + ChatColor.ITALIC + "You cannot fly in KOTL!");
        }
        for (PotionEffect potionEffect : player.getActivePotionEffects()) {
            player.removePotionEffect(potionEffect.getType());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || player.getGameMode() == GameMode.SPECTATOR) return;
        Location to = event.getTo();
        if (to.getWorld().getName().equals(game.world) && game.area.contains(to)) {
            switch (event.getCause()) {
            case ENDER_PEARL:
            case CHORUS_FRUIT:
                event.setCancelled(true);
                spawnPlayer(player);
                player.sendMessage("" + ChatColor.RED + ChatColor.ITALIC + "You cannot ender warp in KOTL!");
                break;
            default: break;
            }
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!player.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(player.getLocation())) return;
        if (game.state != State.CLIMB) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getDamager() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        Player damager = (Player) event.getDamager();
        ItemStack hand = damager.getInventory().getItemInMainHand();
        if (hand != null && hand.getAmount() != 0) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.LOW)
    void onPlayerLaunchProjectile(PlayerLaunchProjectileEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(player.getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!projectile.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(projectile.getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    void onPlayerDropItem(PlayerDropItemEvent event) {
        if (game.state != State.CLIMB) return;
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(player.getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!entity.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(entity.getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (game.state != State.CLIMB) return;
        Location spawnLocation = event.getSpawnLocation();
        if (game.goal.contains(spawnLocation) && spawnLocation.getWorld().getName().equals(game.world)) {
            Location location = randomSpawnLocation();
            if (location != null) {
                event.setSpawnLocation(location);
            }
        }
    }

    public void win(Player winner, int score) {
        game.winners.add(winner.getUniqueId());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + winner.getName() + " Climber LadderKing KingOfTheLadder QueenOfTheLadder VineClimber");
        World world = getWorld();
        for (Player player: getWorld().getPlayers()) {
            if (!game.area.contains(player.getLocation())) continue;
            player.sendTitle(ChatColor.GOLD + winner.getName(),
                             ChatColor.GOLD + "Wins King of the Ladder!",
                             0, 20, 60);
            player.sendMessage(ChatColor.GOLD + winner.getName() + " wins King of the Ladder!");
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
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(player.getLocation())) return;
        event.setRespawnLocation(randomSpawnLocation(player));
    }

    @EventHandler
    public void onPlayerSidebar(PlayerSidebarEvent event) {
        if (game == null || game.state != State.CLIMB) return;
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(game.world)) return;
        if (!game.area.contains(player.getLocation())) return;
        List<UUID> uuids = new ArrayList<>(game.scores.keySet());
        Collections.sort(uuids, (b, a) -> Integer.compare(game.scores.get(a), game.scores.get(b)));
        List<Component> lines = new ArrayList<>();
        int seconds = game.timeLeft / 20;
        int minutes = seconds / 60;
        lines.add(Component.text("King of the Ladder ").color(TextColor.color(0x00FF00))
                  .append(Component.text(String.format("%02d:%02d", minutes, seconds % 60)).color(TextColor.color(0xAAAAAA))));
        for (int i = 0; i < 5; i += 1) {
            if (i >= uuids.size()) break;
            UUID uuid = uuids.get(i);
            Player other = Bukkit.getPlayer(uuid);
            if (other == null) return;
            int score = game.scores.get(uuid) / 20;
            if (score == 0) break;
            lines.add(Component.empty()
                      .append(Component.text(score + " ").color(TextColor.color(0xFFFF00)))
                      .append(Component.text(other.getName()).color(TextColor.color(0xFFFFFF))));
        }
        int playerScore = game.scores.computeIfAbsent(player.getUniqueId(), u -> 0) / 20;
        lines.add(Component.text("Your score ").append(Component.text("" + playerScore).color(TextColor.color(0xFFFF00))));
        if (game.goal.contains(player.getLocation())) {
            lines.add(Component.text("Stay in the goal!").color(TextColor.color(0xFFFF00)).decorate(TextDecoration.BOLD));
        }
        event.add(this, Priority.HIGH, lines);
    }

    public World getWorld() {
        return Bukkit.getWorld(game.world);
    }

    void run() {
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
        }
        if (goalPlayers.size() == 1) {
            game.scores.compute(goalPlayers.get(0).getUniqueId(), (u, i) -> (i != null ? i : 0) + 1);
        }
        game.timeLeft -= 1;
        if (game.timeLeft <= 0) {
            Player winner = null;
            int max = 0;
            for (Map.Entry<UUID, Integer> entry : game.scores.entrySet()) {
                UUID uuid = entry.getKey();
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                int score = entry.getValue();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
                if (score > max) {
                    winner = player;
                    max = score;
                }
            }
            if (winner != null) win(winner, max);
            setupState(State.PAUSE);
        }
    }
}

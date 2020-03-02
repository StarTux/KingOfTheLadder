package com.cavetale.kotl;

import com.google.gson.Gson;
import com.winthier.generic_events.GenericEvents;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Data;
import lombok.Value;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

public final class KOTLPlugin extends JavaPlugin implements Listener {
    private static final String META_AREA = "kotl.area";
    private Game game;
    private Scoreboard scoreboard;
    private Objective objective;
    private transient int spawnHeight;

    // --- Java Plugin

    @Override
    public void onEnable() {
        scoreboard = getServer().getScoreboardManager().getNewScoreboard();
        objective = scoreboard.registerNewObjective("kotl", "dummy", ChatColor.GOLD + "KOTL");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        getCommand("kotl").setExecutor((a, b, c, d) -> onGameCommand(a, d));
        getCommand("kotla").setExecutor((a, b, c, d) -> onAdminCommand(a, d));
        getServer().getPluginManager().registerEvents(this, this);
        loadGame();
    }

    @Override
    public void onDisable() {
    }

    // --- Command Interface

    public boolean onGameCommand(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;
        spawnPlayer(player);
        return true;
    }

    public boolean onAdminCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(game.toString());
            return false;
        }
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
            saveGame();
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
            sender.sendMessage(ChatColor.YELLOW + "Area set to " + sel.a + "-" + sel.b);
            return true;
        }
        case "setgoal": {
            if (args.length != 1) return false;
            if (player == null) {
                sender.sendMessage("[KOTL] Player expected");
                return true;
            }
            Block block = player.getLocation().getBlock().getRelative(0, -1, 0);
            game.goalBlock = Vec.v(block.getX(), block.getY(), block.getZ());
            saveGame();
            sender.sendMessage(ChatColor.YELLOW + "Goal block set to " + game.goalBlock);
            return true;
        }
        case "setgoalmaterial": {
            if (args.length != 2) return false;
            if (player == null) {
                sender.sendMessage("[KOTL] Player expected");
                return true;
            }
            try {
                game.goalMaterial = Material.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException iae) {
                sender.sendMessage(ChatColor.RED + "Unknown material: " + args[1]);
                return true;
            }
            saveGame();
            sender.sendMessage(ChatColor.YELLOW + "Goal material set to " + game.goalMaterial);
            return true;
        }
        case "addspawn": {
            if (args.length != 1) return false;
            if (player == null) {
                sender.sendMessage("[KOTL] Player expected");
                return true;
            }
            Block block = player.getLocation().getBlock();
            Vec spawnBlock = Vec.v(block.getX(), block.getY(), block.getZ());
            game.spawnBlocks.add(spawnBlock);
            saveGame();
            sender.sendMessage(ChatColor.YELLOW + "Spawn block added: " + spawnBlock);
            return true;
        }
        case "clearspawn": {
            if (args.length != 1) return false;
            game.spawnBlocks.clear();
            saveGame();
            sender.sendMessage(ChatColor.YELLOW + "Spawn blocks cleared");
            return true;
        }
        case "clearwinners": {
            if (args.length != 1) return false;
            game.winners.clear();
            saveGame();
            sender.sendMessage(ChatColor.YELLOW + "Winners cleared");
            return true;
        }
        case "setlevel": {
            if (args.length != 3) return false;
            UUID uuid = GenericEvents.cachedPlayerUuid(args[1]);
            if (uuid == null) {
                sender.sendMessage("Unknown player: " + args[1]);
                return true;
            }
            int level;
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException nfe) {
                sender.sendMessage("Number expected: " + args[2]);
                return true;
            }
            setPlayerLevel(uuid, level);
            saveGame();
            sender.sendMessage(ChatColor.YELLOW + "Set level of " + args[1] + " to " + level);
            return true;
        }
        case "clearlevels": {
            game.playerLevels.clear();
            saveGame();
            setupScores();
            sender.sendMessage(ChatColor.YELLOW + "Set all player levels to 0");
            return true;
        }
        default: return false;
        }
    }

    // --- Player Utility

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

    boolean hasMeta(Player player, String key) {
        for (MetadataValue value: player.getMetadata(key)) {
            if (value.getOwningPlugin() == this) return true;
        }
        return false;
    }

    int getPlayerLevel(Player player) {
        UUID uuid = player.getUniqueId();
        Integer result = game.playerLevels.get(uuid);
        if (result == null) return 0;
        return result;
    }

    void setPlayerLevel(UUID uuid, int level) {
        game.playerLevels.put(uuid, level);
        objective.getScore(GenericEvents.cachedPlayerName(uuid)).setScore(level);
    }

    void setPlayerLevel(Player player, int level) {
        game.playerLevels.put(player.getUniqueId(), level);
        objective.getScore(player.getName()).setScore(level);
        // Remember to save!
    }

    Rect getSelection(Player player) {
        int ax, ay, az, bx, by, bz;
        try {
            ax = player.getMetadata("SelectionAX").get(0).asInt();
            ay = player.getMetadata("SelectionAY").get(0).asInt();
            az = player.getMetadata("SelectionAZ").get(0).asInt();
            bx = player.getMetadata("SelectionBX").get(0).asInt();
            by = player.getMetadata("SelectionBY").get(0).asInt();
            bz = player.getMetadata("SelectionBZ").get(0).asInt();
        } catch (Exception e) {
            return null;
        }
        return new Rect(Vec.v(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz)),
                        Vec.v(Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz)));
    }

    // --- Game State

    enum State {
        PAUSE, // Nothing's happening
        CLIMB; // Climb and PVP
    }

    void setupState(State state) {
        switch (state) {
        case PAUSE:
            break;
        case CLIMB: {
            // Check if everything is set
            if (game.spawnBlocks.isEmpty()) {
                getServer().broadcast(ChatColor.RED + "[KOTL] No spawn blocks configured!", "kotl.admin");
                return;
            }
            if (game.goalBlock.equals(Vec.ZERO)) {
                getServer().broadcast(ChatColor.RED + "[KOTL] No goal block configured!", "kotl.admin");
                return;
            }
            if (game.area.a.equals(Vec.ZERO) && game.area.b.equals(Vec.ZERO)) {
                getServer().broadcast(ChatColor.RED + "[KOTL] No area configured!", "kotl.admin");
                return;
            }
            World world = getServer().getWorld(game.world);
            if (world == null) {
                getServer().broadcast("[KOTL] World not found: " + game.world, "kotl.admin");
                return;
            }
            Block goalBlock = world.getBlockAt(game.goalBlock.x, game.goalBlock.y, game.goalBlock.z);
            goalBlock.setType(game.goalMaterial);
            for (final Player player: world.getPlayers()) {
                if (player.isOp()) continue;
                if (!game.area.contains(player.getLocation())) continue;
                spawnPlayer(player);
                player.sendTitle("" + ChatColor.GOLD + ChatColor.ITALIC + "GO!",
                                 "" + ChatColor.GOLD + "King of the Ladder",
                                 0, 20, 60);
                player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            break;
        }
        default:
            break;
        }
        game.state = state;
        saveGame();
    }

    @Value
    static final class Vec {
        static final Vec ZERO = new Vec(0, 0, 0);
        int x, y, z;
        static Vec v(int x, int y, int z) {
            return new Vec(x, y, z);
        }
        boolean isBlock(Block block) {
            return x == block.getX()
                && y == block.getY()
                && z == block.getZ();
        }
        @Override
        public String toString() {
            return String.format("(%d,%d,%d)", x, y, z);
        }
    }

    @Value
    static final class Rect {
        Vec a, b;
        boolean contains(int x, int y, int z) {
            return x >= a.x
                && y >= a.y
                && z >= a.z
                && x <= b.x
                && y <= b.y
                && z <= b.z;
        }
        boolean contains(Location location) {
            return contains(location.getBlockX(),
                            location.getBlockY(),
                            location.getBlockZ());
        }
    }

    @Data
    static final class Game {
        State state;
        String world;
        Rect area;
        Vec goalBlock;
        Material goalMaterial;
        Set<Vec> spawnBlocks;
        List<UUID> winners;
        Map<UUID, Integer> playerLevels;
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
            game.goalBlock = Vec.ZERO;
            game.goalMaterial = Material.BRICKS;
            game.spawnBlocks = new HashSet<>();
            game.winners = new ArrayList<>();
            game.playerLevels = new HashMap<>();
        }
        spawnHeight = 0;
        for (Vec spawnBlock: game.spawnBlocks) {
            spawnHeight = Math.max(spawnHeight, spawnBlock.y);
        }
        setupScores();
    }

    void setupScores() {
        objective.unregister();
        objective = scoreboard.registerNewObjective("kotl", "dummy", ChatColor.GOLD + "KOTL");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (Map.Entry<UUID, Integer> entry: game.playerLevels.entrySet()) {
            String name = GenericEvents.cachedPlayerName(entry.getKey());
            if (name == null) continue;
            objective.getScore(name).setScore(entry.getValue());
        }
    }

    void saveGame() {
        Gson gson = new Gson();
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
        Player player = event.getPlayer();
        if (player.getWorld().getName().equals(game.world)
            && game.area.contains(player.getLocation())) {
            if (hasMeta(player, META_AREA)) {
                Location loc = player.getLocation();
                if (!player.isOp() && game.state == State.CLIMB && loc.getBlockY() > spawnHeight + 1 && playerCarriesItem(player)) {
                    spawnPlayer(player);
                    player.sendMessage("" + ChatColor.RED + ChatColor.ITALIC + "You cannot wear or hold any items in KOTL!");
                }
            } else {
                player.setMetadata(META_AREA, new FixedMetadataValue(this, true));
                player.setScoreboard(scoreboard);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("Entering King of the Ladder"));
            }
        } else {
            if (hasMeta(player, META_AREA)) {
                player.removeMetadata(META_AREA, this);
                player.setScoreboard(getServer().getScoreboardManager().getMainScoreboard());
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("Leaving King of the Ladder"));
            }
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (game.state != State.CLIMB) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!player.getWorld().getName().equals(game.world)) return;
        if (!hasMeta(player, META_AREA)) return;
        if (!(event.getDamager() instanceof Player)) return;
        Player damager = (Player) event.getDamager();
        if (!game.area.contains(player.getLocation())) return;
        if (!game.area.contains(damager.getLocation())) return;
        event.setCancelled(false);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!block.getWorld().getName().equals(game.world)) return;
        Player player = event.getPlayer();
        if (!hasMeta(player, META_AREA)) return;
        event.setCancelled(false);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onBlockDamage(BlockDamageEvent event) {
        if (game.state != State.CLIMB) return;
        Block block = event.getBlock();
        if (!block.getWorld().getName().equals(game.world)) return;
        if (!game.goalBlock.isBlock(block)) return;
        Player player = event.getPlayer();
        int level = getPlayerLevel(player);
        if (level > 5) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 20 * level, 1));
        } else if (level > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 20 * level, 0));
        }
        event.setCancelled(false);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (game.state != State.CLIMB) return;
        Block block = event.getBlock();
        if (!block.getWorld().getName().equals(game.world)) return;
        if (!game.goalBlock.isBlock(block)) return;
        Player winner = event.getPlayer();
        int level = getPlayerLevel(winner) + 1;
        setPlayerLevel(winner, level);
        game.winners.add(winner.getUniqueId());
        setupState(State.PAUSE); // Will save
        block.setType(Material.AIR);
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 1.0f, 2.0f);
        block.getWorld().spawnParticle(Particle.BLOCK_DUST, block.getLocation().add(0.5, 0.5, 0.5), 64, 0.25, 0.25, 0.25, 0.0, game.goalMaterial.createBlockData());
        for (Player player: block.getWorld().getPlayers()) {
            if (!game.area.contains(player.getLocation())) continue;
            player.sendTitle(ChatColor.GOLD + winner.getName(),
                             ChatColor.GOLD + "Wins King of the Ladder level " + level + "!",
                             0, 20, 60);
            player.sendMessage(ChatColor.GOLD + winner.getName() + " wins King of the Ladder level " + level + "!");
            player.playSound(player.getEyeLocation(), Sound.ENTITY_WITHER_DEATH, SoundCategory.MASTER, 0.5f, 1.0f);
        }
        Firework firework = block.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), Firework.class, (fw) -> {
                FireworkMeta meta = fw.getFireworkMeta();
                for (int i = 0; i <= level; i += 1) {
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
        if (!hasMeta(player, META_AREA)) return;
        event.setRespawnLocation(randomSpawnLocation(player));
    }
}

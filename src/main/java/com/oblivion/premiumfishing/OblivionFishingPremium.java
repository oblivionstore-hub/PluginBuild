package com.oblivion.premiumfishing;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class OblivionFishingPremium extends JavaPlugin implements Listener {
    private final Map<String, Loot> loot = new LinkedHashMap<>();
    private final Map<String, Rarity> rarities = new LinkedHashMap<>();
    private final Map<UUID, Profile> profiles = new HashMap<>();
    private NamespacedKey lootKey, rarityKey;

    @Override public void onEnable() {
        saveDefaultConfig();
        lootKey = new NamespacedKey(this, "loot_id");
        rarityKey = new NamespacedKey(this, "rarity");
        loadTables();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("OblivionFishingPremium loaded " + loot.size() + " loot entries.");
    }

    private void loadTables() {
        loot.clear(); rarities.clear();
        ConfigurationSection r = getConfig().getConfigurationSection("rarities");
        if (r != null) for (String id : r.getKeys(false)) {
            ConfigurationSection s = r.getConfigurationSection(id);
            if (s != null) rarities.put(id.toUpperCase(Locale.ROOT), new Rarity(id.toUpperCase(Locale.ROOT), s.getString("display", id), s.getString("color", "&f"), Math.max(0, s.getDouble("weight", 0))));
        }
        ConfigurationSection l = getConfig().getConfigurationSection("loot");
        if (l != null) for (String id : l.getKeys(false)) {
            ConfigurationSection s = l.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", true)) continue;
            String rarity = s.getString("rarity", "COMMON").toUpperCase(Locale.ROOT);
            Material material = Material.matchMaterial(s.getString("material", "COD"));
            if (material == null || !material.isItem() || !rarities.containsKey(rarity)) { getLogger().warning("Invalid loot: " + id); continue; }
            double weight = s.getDouble("weight", 1);
            if (weight > 0) loot.put(id, new Loot(id, rarity, material, Math.max(1, s.getInt("min", 1)), Math.max(1, s.getInt("max", 1)), weight, s.getString("name", ""), s.getStringList("lore"), Math.max(0, s.getInt("xp", 1))));
        }
    }

    @EventHandler public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)) return;
        Player player = event.getPlayer();
        Profile profile = profiles.computeIfAbsent(player.getUniqueId(), k -> new Profile());
        Loot selected = rollLoot(player, profile);
        if (selected == null) { caught.remove(); player.sendMessage(color(getConfig().getString("settings.no-loot-message", "&7No catch."))); return; }
        int amount = ThreadLocalRandom.current().nextInt(selected.min, Math.max(selected.min, selected.max) + 1);
        ItemStack reward = new ItemStack(selected.material, Math.min(amount, selected.material.getMaxStackSize()));
        ItemMeta meta = reward.getItemMeta();
        if (meta != null) {
            if (!selected.name.isBlank()) meta.setDisplayName(color(selected.name));
            List<String> lore = new ArrayList<>(); selected.lore.forEach(line -> lore.add(color(line)));
            lore.add(color("&8Rarity: " + rarities.get(selected.rarity).color + rarities.get(selected.rarity).display));
            lore.add(color("&8OblivionFishing Premium"));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(lootKey, PersistentDataType.STRING, selected.id);
            meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, selected.rarity);
            reward.setItemMeta(meta);
        }
        if (getConfig().getBoolean("settings.replace-vanilla-catch", true)) caught.setItemStack(reward);
        else { caught.remove(); player.getInventory().addItem(reward).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left)); }

        profile.total++; profile.discovered.merge(selected.id, 1, Integer::sum);
        profile.xp += getConfig().getInt("progression.xp-per-catch", 5) + selected.xp;
        if (rank(selected.rarity) >= rank("RARE")) profile.sinceRare = 0; else profile.sinceRare++;
        if (rank(selected.rarity) >= rank("EPIC")) profile.sinceEpic = 0; else profile.sinceEpic++;
        String itemName = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : selected.material.name();
        player.sendMessage(color(getConfig().getString("settings.catch-message", "&7Caught {item}").replace("{item}", itemName).replace("{rarity}", rarities.get(selected.rarity).display)));
        player.sendActionBar(color("&bFishing XP: &f" + profile.xp + " &8| " + rarities.get(selected.rarity).color + rarities.get(selected.rarity).display));
        if (getConfig().getBoolean("settings.broadcast-rare-catches", true) && rank(selected.rarity) >= rank(getConfig().getString("settings.broadcast-from-rarity", "EPIC"))) {
            Bukkit.broadcastMessage(color(getConfig().getString("settings.rare-broadcast", "&d{player} caught {item}").replace("{player}", player.getName()).replace("{item}", itemName).replace("{rarity}", rarities.get(selected.rarity).display)));
        }
    }

    private Loot rollLoot(Player player, Profile profile) {
        double total = 0; Map<String, Double> adjusted = new LinkedHashMap<>();
        long time = player.getWorld().getTime();
        boolean night = time >= 13000 && time <= 23000;
        boolean rain = player.getWorld().hasStorm();
        boolean specialBiome = getConfig().getStringList("environment.special-biomes").contains(player.getLocation().getBlock().getBiome().name());
        for (Rarity r : rarities.values()) {
            double w = r.weight;
            if (rank(r.id) >= rank("RARE")) {
                if (night && getConfig().getBoolean("environment.night-bonus", true)) w *= getConfig().getDouble("environment.rare-multiplier-at-night", 1.25);
                if (rain && getConfig().getBoolean("environment.rain-bonus", true)) w *= getConfig().getDouble("environment.rare-multiplier-in-rain", 1.2);
                if (specialBiome && getConfig().getBoolean("environment.biome-bonus", true)) w *= getConfig().getDouble("environment.rare-multiplier-in-special-biome", 1.35);
            }
            if (getConfig().getBoolean("progression.pity-enabled", true)) {
                if (rank(r.id) >= rank("RARE") && profile.sinceRare >= getConfig().getInt("progression.rare-pity-after", 35)) w *= getConfig().getDouble("progression.rare-pity-multiplier", 2.0);
                if (rank(r.id) >= rank("EPIC") && profile.sinceEpic >= getConfig().getInt("progression.epic-pity-after", 180)) w *= getConfig().getDouble("progression.epic-pity-multiplier", 2.0);
            }
            adjusted.put(r.id, w); total += w;
        }
        if (total <= 0) return null;
        double roll = ThreadLocalRandom.current().nextDouble(total); String chosen = null;
        for (Rarity r : rarities.values()) { roll -= adjusted.getOrDefault(r.id, 0.0); if (roll < 0) { chosen = r.id; break; } }
        if (chosen == null) return null;
        final String chosenRarity = chosen;
        List<Loot> group = loot.values().stream().filter(e -> e.rarity.equals(chosenRarity)).toList();
        if (group.isEmpty()) return null;
        double sum = group.stream().mapToDouble(e -> e.weight).sum(); double itemRoll = ThreadLocalRandom.current().nextDouble(sum);
        for (Loot e : group) { itemRoll -= e.weight; if (itemRoll < 0) return e; }
        return group.get(group.size() - 1);
    }

    private int rank(String rarity) { return switch (rarity.toUpperCase(Locale.ROOT)) { case "COMMON" -> 0; case "UNCOMMON" -> 1; case "RARE" -> 2; case "EPIC" -> 3; case "LEGENDARY" -> 4; case "MYTHIC" -> 5; default -> -1; }; }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "journal" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("oblivionfishing.reload")) { sender.sendMessage(color("&cNo permission.")); return true; }
            reloadConfig(); loadTables(); sender.sendMessage(color("&aOblivionFishing config reloaded.")); return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("This command requires a player."); return true; }
        Profile profile = profiles.computeIfAbsent(p.getUniqueId(), k -> new Profile());
        if (sub.equals("stats")) {
            int perLevel = Math.max(1, getConfig().getInt("progression.xp-per-level", 100));
            int level = Math.min(getConfig().getInt("progression.max-level", 50), profile.xp / perLevel + 1);
            p.sendMessage(color("&b&lOblivion Fishing Profile"));
            p.sendMessage(color("&7Total catches: &f" + profile.total));
            p.sendMessage(color("&7Fishing XP: &f" + profile.xp + " &8| &7Level: &f" + level));
            p.sendMessage(color("&7Discovered: &f" + profile.discovered.size() + "/" + loot.size())); return true;
        }
        openJournal(p); return true;
    }

    private void openJournal(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color(getConfig().getString("journal.title", "&1&lOblivion Fishing Journal")));
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE); ItemMeta fm = filler.getItemMeta(); if (fm != null) { fm.setDisplayName(" "); filler.setItemMeta(fm); }
        for (int i=0;i<54;i++) inv.setItem(i, filler);
        Profile profile = profiles.computeIfAbsent(p.getUniqueId(), k -> new Profile());
        List<Loot> entries = new ArrayList<>(loot.values()); entries.sort(Comparator.comparingInt(e -> rank(e.rarity)));
        for (int i=0;i<Math.min(45, entries.size());i++) {
            Loot e = entries.get(i); boolean found = profile.discovered.containsKey(e.id);
            ItemStack icon = new ItemStack(found ? e.material : Material.GRAY_STAINED_GLASS_PANE); ItemMeta m = icon.getItemMeta();
            if (m != null) { m.setDisplayName(color(found ? e.name : getConfig().getString("journal.undiscovered-name", "&7???"))); List<String> lines = new ArrayList<>();
                if (found) { e.lore.forEach(line -> lines.add(color(line))); lines.add(color("&7Caught: &f" + profile.discovered.get(e.id))); lines.add(color("&7Rarity: " + rarities.get(e.rarity).display)); }
                else lines.add(color("&8Belum ditemukan")); m.setLore(lines); icon.setItemMeta(m); }
            inv.setItem(i, icon);
        }
        ItemStack book = new ItemStack(Material.BOOK); ItemMeta bm = book.getItemMeta(); if (bm != null) { bm.setDisplayName(color("&bFishing Profile")); bm.setLore(List.of(color("&7Catches: &f" + profile.total), color("&7XP: &f" + profile.xp), color("&7Discovered: &f" + profile.discovered.size() + "/" + loot.size()), color("&7Use /fishing stats"))); book.setItemMeta(bm); } inv.setItem(49, book);
        ItemStack close = new ItemStack(Material.BARRIER); ItemMeta cm = close.getItemMeta(); if (cm != null) { cm.setDisplayName(color("&cClose")); close.setItemMeta(cm); } inv.setItem(53, close); p.openInventory(inv);
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!ChatColor.stripColor(e.getView().getTitle()).contains("Oblivion Fishing Journal")) return;
        e.setCancelled(true); if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.BARRIER) p.closeInventory();
    }

    private record Rarity(String id, String display, String color, double weight) {}
    private record Loot(String id, String rarity, Material material, int min, int max, double weight, String name, List<String> lore, int xp) {}
    private static final class Profile { int total, xp, sinceRare, sinceEpic; final Map<String,Integer> discovered = new HashMap<>(); }
}

package com.geo99567.blockbank;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public final class BlockBankPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<UUID, Inventory> sellMenus = new HashMap<>();
    private File balancesFile;
    private YamlConfiguration balanceData;
    private NamespacedKey actionKey;
    private NamespacedKey valueKey;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.##");

    @Override public void onEnable() {
        saveDefaultConfig();
        actionKey = new NamespacedKey(this, "action");
        valueKey = new NamespacedKey(this, "value");
        balancesFile = new File(getDataFolder(), "balances.yml");
        balanceData = YamlConfiguration.loadConfiguration(balancesFile);
        for (String key : balanceData.getKeys(false)) {
            try { balances.put(UUID.fromString(key), balanceData.getDouble(key)); }
            catch (IllegalArgumentException ignored) { getLogger().warning("Skipping invalid balance UUID: " + key); }
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("shop")).setExecutor(this);
        Objects.requireNonNull(getCommand("sell")).setExecutor(this);
        Objects.requireNonNull(getCommand("money")).setExecutor(this);
        Objects.requireNonNull(getCommand("shopadmin")).setExecutor(this);
        getLogger().info("BlockBank enabled.");
    }

    @Override public void onDisable() { saveBalances(); }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private String currency() { return getConfig().getString("currency-symbol", "$"); }
    private String money(double amount) { return currency() + moneyFormat.format(amount); }
    private double balance(UUID id) { return balances.computeIfAbsent(id, ignored -> getConfig().getDouble("starting-balance", 100.0)); }
    private void setBalance(UUID id, double amount) { balances.put(id, Math.max(0, amount)); }
    private void saveBalances() {
        if (balanceData == null) return;
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) balanceData.set(entry.getKey().toString(), entry.getValue());
        try { balanceData.save(balancesFile); }
        catch (IOException e) { getLogger().severe("Could not save balances.yml: " + e.getMessage()); }
    }
    private ItemStack named(Material material, String title, List<String> lore, String action, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(title));
            if (lore != null) meta.setLore(lore.stream().map(this::color).toList());
            if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            if (value != null) meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
            item.setItemMeta(meta);
        }
        return item;
    }
    private String itemName(Material m) {
        String[] words = m.name().toLowerCase(Locale.ROOT).split("_");
        StringJoiner joiner = new StringJoiner(" ");
        for (String word : words) joiner.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        return joiner.toString();
    }
    private List<String> categoryIds() {
        ConfigurationSection section = getConfig().getConfigurationSection("categories");
        return section == null ? new ArrayList<>() : new ArrayList<>(section.getKeys(false));
    }
    private void openShop(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&8BlockBank &7» &aCategories"));
        List<String> ids = categoryIds();
        int slot = 10;
        for (String id : ids) {
            String path = "categories." + id;
            Material icon = Material.matchMaterial(getConfig().getString(path + ".icon", "CHEST"));
            if (icon == null || !icon.isItem()) icon = Material.CHEST;
            inv.setItem(slot, named(icon, "&a" + getConfig().getString(path + ".name", id),
                    List.of("&7Click to browse this category"), "category", id));
            slot++;
            if (slot == 17) slot = 19;
            if (slot >= 26) break;
        }
        inv.setItem(22, named(Material.GOLD_INGOT, "&6Your balance",
                List.of("&f" + money(balance(player.getUniqueId()))), null, null));
        player.openInventory(inv);
    }
    private void openCategory(Player player, String category) {
        String path = "categories." + category;
        ConfigurationSection items = getConfig().getConfigurationSection(path + ".items");
        if (items == null) { player.sendMessage(color("&cThat category has no items.")); openShop(player); return; }
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Shop: &a" + getConfig().getString(path + ".name", category)));
        int slot = 0;
        for (String key : items.getKeys(false)) {
            if (!getConfig().getBoolean(path + ".items." + key + ".in-shop", true)) continue;
            Material material = Material.matchMaterial(key);
            if (material == null || !material.isItem()) continue;
            double buy = getConfig().getDouble(path + ".items." + key + ".buy", 0);
            double sell = getConfig().getDouble(path + ".items." + key + ".sell", 0);
            inv.setItem(slot++, named(material, "&f" + itemName(material),
                    List.of("&7Buy: &a" + money(buy), "&7Sell: &e" + money(sell),
                            "&8Left-click to buy 1", "&8Right-click to buy 64"), "buy", category + ":" + key));
            if (slot >= 45) break;
        }
        inv.setItem(49, named(Material.ARROW, "&eBack to categories", List.of(), "back", null));
        player.openInventory(inv);
    }
    private void openSell(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Sell items"));
        for (int i = 45; i < 54; i++) inv.setItem(i, named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), "filler", null));
        inv.setItem(49, named(Material.LIME_STAINED_GLASS_PANE, "&a&lSELL ITEMS",
                List.of("&7Items in the top slots only will be sold.", "&eClick to sell the items you placed here."), "sell-confirm", null));
        sellMenus.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }
    private void openAdmin(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&8BlockBank Admin"));
        int slot = 0;
        for (String category : categoryIds()) {
            ConfigurationSection items = getConfig().getConfigurationSection("categories." + category + ".items");
            if (items == null) continue;
            for (String key : items.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null || !material.isItem()) continue;
                String path = "categories." + category + ".items." + key;
                boolean sellable = getConfig().getBoolean(path + ".sellable", true);
                boolean listed = getConfig().getBoolean(path + ".in-shop", true);
                inv.setItem(slot++, named(material, (listed ? "&a" : "&c") + itemName(material),
                        List.of("&7Category: &f" + category, "&7Buy: &a" + money(getConfig().getDouble(path + ".buy")),
                                "&7Sell: &e" + money(getConfig().getDouble(path + ".sell")),
                                "&7Sellable: " + (sellable ? "&aYes" : "&cNo"),
                                "&eLeft-click: toggle shop listing", "&eRight-click: toggle sellable"),
                        "admin-item", category + ":" + key));
                if (slot >= 45) break;
            }
            if (slot >= 45) break;
        }
        inv.setItem(45, named(Material.WRITABLE_BOOK, "&eAdmin commands",
                List.of("&7/shopadmin category <id> <name> <icon>",
                        "&7/shopadmin add <category> <MATERIAL> <buy> <sell>",
                        "&7/shopadmin price <MATERIAL> <buy> <sell>",
                        "&7/shopadmin sellable <MATERIAL> <true|false>",
                        "&7/shopadmin list <MATERIAL> <true|false>"), null, null));
        player.openInventory(inv);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("shop")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            openShop(p); return true;
        }
        if (cmd.equals("sell")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            openSell(p); return true;
        }
        if (cmd.equals("money")) {
            if (args.length == 0 && sender instanceof Player p) {
                sender.sendMessage(color("&aBalance: &f" + money(balance(p.getUniqueId())))); return true;
            }
            if (args.length == 1 && sender.hasPermission("blockbank.admin")) {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { sender.sendMessage(color("&cThat player must be online.")); return true; }
                sender.sendMessage(color("&a" + target.getName() + "'s balance: &f" + money(balance(target.getUniqueId())))); return true;
            }
            sender.sendMessage(color("&eUsage: /money [player]")); return true;
        }
        if (cmd.equals("shopadmin")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Use this command in-game."); return true; }
            if (!p.isOp() && !p.hasPermission("blockbank.admin")) { p.sendMessage(color("&cYou need operator or blockbank.admin permission.")); return true; }
            if (args.length == 0) { openAdmin(p); return true; }
            String sub = args[0].toLowerCase(Locale.ROOT);
            try {
                switch (sub) {
                    case "category" -> {
                        if (args.length < 4) { p.sendMessage(color("&e/shopadmin category <id> <name> <icon-material>")); return true; }
                        Material icon = Material.matchMaterial(args[3].toUpperCase(Locale.ROOT));
                        if (icon == null || !icon.isItem()) { p.sendMessage(color("&cInvalid icon material.")); return true; }
                        String path = "categories." + args[1].toLowerCase(Locale.ROOT);
                        getConfig().set(path + ".name", String.join(" ", Arrays.copyOfRange(args, 2, args.length - 1)));
                        getConfig().set(path + ".icon", icon.name());
                        if (getConfig().getConfigurationSection(path + ".items") == null) {
                            getConfig().set(path + ".items", new LinkedHashMap<String, Object>());
                        }
                        saveConfig(); p.sendMessage(color("&aCategory saved.")); return true;
                    }
                    case "add" -> {
                        if (args.length != 5) { p.sendMessage(color("&e/shopadmin add <category> <MATERIAL> <buy> <sell>")); return true; }
                        String category = args[1].toLowerCase(Locale.ROOT);
                        Material m = Material.matchMaterial(args[2].toUpperCase(Locale.ROOT));
                        double buy = Double.parseDouble(args[3]), sell = Double.parseDouble(args[4]);
                        if (m == null || !m.isItem() || buy < 0 || sell < 0 || !getConfig().isConfigurationSection("categories." + category)) {
                            p.sendMessage(color("&cInvalid material, price, or category.")); return true;
                        }
                        String path = "categories." + category + ".items." + m.name();
                        getConfig().set(path + ".buy", buy); getConfig().set(path + ".sell", sell);
                        getConfig().set(path + ".sellable", true); getConfig().set(path + ".in-shop", true);
                        saveConfig(); p.sendMessage(color("&aAdded " + m.name() + " to " + category + ".")); return true;
                    }
                    case "price" -> {
                        if (args.length != 4) { p.sendMessage(color("&e/shopadmin price <MATERIAL> <buy> <sell>")); return true; }
                        Material m = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
                        double buy = Double.parseDouble(args[2]), sell = Double.parseDouble(args[3]);
                        String path = findItemPath(m);
                        if (path == null || buy < 0 || sell < 0) { p.sendMessage(color("&cItem not found or price invalid.")); return true; }
                        getConfig().set(path + ".buy", buy); getConfig().set(path + ".sell", sell); saveConfig();
                        p.sendMessage(color("&aPrices updated.")); return true;
                    }
                    case "sellable", "list" -> {
                        if (args.length != 3 || !args[2].equalsIgnoreCase("true") && !args[2].equalsIgnoreCase("false")) {
                            p.sendMessage(color("&e/shopadmin " + sub + " <MATERIAL> <true|false>")); return true;
                        }
                        Material m = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
                        String path = findItemPath(m);
                        if (path == null) { p.sendMessage(color("&cThat material is not configured in the shop.")); return true; }
                        getConfig().set(path + (sub.equals("list") ? ".in-shop" : ".sellable"), Boolean.parseBoolean(args[2]));
                        saveConfig(); p.sendMessage(color("&aSetting updated.")); return true;
                    }
                    default -> p.sendMessage(color("&e/shopadmin [category|add|price|sellable|list] ..."));
                }
            } catch (NumberFormatException e) { p.sendMessage(color("&cPrices must be numbers.")); }
            return true;
        }
        return false;
    }
    private String findItemPath(Material material) {
        if (material == null) return null;
        for (String category : categoryIds()) {
            String path = "categories." + category + ".items." + material.name();
            if (getConfig().isConfigurationSection(path)) return path;
        }
        return null;
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        String title = event.getView().getTitle();
        boolean isSell = title.equals(color("&8Sell items"));
        boolean isShop = title.startsWith(color("&8BlockBank")) || title.startsWith(color("&8Shop:"));
        boolean isAdmin = title.equals(color("&8BlockBank Admin"));
        if (!isSell && !isShop && !isAdmin) return;
        if (event.getClickedInventory() == null) return;
        if (isSell) {
            if (event.getClickedInventory().equals(top)) {
                int slot = event.getRawSlot();
                if (slot >= 45) {
                    event.setCancelled(true);
                    if (slot == 49) sellContents(player, top);
                }
            }
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = clicked.getItemMeta().getPersistentDataContainer().get(valueKey, PersistentDataType.STRING);
        if (action == null) return;
        if (action.equals("category")) { openCategory(player, value); return; }
        if (action.equals("back")) { openShop(player); return; }
        if (action.equals("buy") && value != null) {
            String[] parts = value.split(":", 2);
            Material m = Material.matchMaterial(parts[1]);
            if (m == null) return;
            String path = "categories." + parts[0] + ".items." + parts[1];
            int amount = event.getClick() == ClickType.RIGHT ? 64 : 1;
            double price = getConfig().getDouble(path + ".buy") * amount;
            if (balance(player.getUniqueId()) < price) { player.sendMessage(color("&cYou need " + money(price) + " to buy that.")); return; }
            ItemStack stack = new ItemStack(m, amount);
            if (player.getInventory().firstEmpty() == -1) { player.sendMessage(color("&cYour inventory is full.")); return; }
            setBalance(player.getUniqueId(), balance(player.getUniqueId()) - price);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            saveBalances();
            player.sendMessage(color("&aBought " + amount + "x " + itemName(m) + " for " + money(price) + "."));
            return;
        }
        if (isAdmin && action.equals("admin-item") && value != null) {
            String[] parts = value.split(":", 2);
            String path = "categories." + parts[0] + ".items." + parts[1];
            if (event.isLeftClick()) getConfig().set(path + ".in-shop", !getConfig().getBoolean(path + ".in-shop", true));
            else if (event.isRightClick()) getConfig().set(path + ".sellable", !getConfig().getBoolean(path + ".sellable", true));
            saveConfig(); openAdmin(player);
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(color("&8Sell items")) && event.getRawSlots().stream().anyMatch(slot -> slot >= 45)) event.setCancelled(true);
        String title = event.getView().getTitle();
        if ((title.startsWith(color("&8BlockBank")) || title.startsWith(color("&8Shop:")) || title.equals(color("&8BlockBank Admin")))
                && !title.equals(color("&8Sell items"))) event.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = sellMenus.remove(player.getUniqueId());
        if (inv == null || !inv.equals(event.getInventory())) return;
        returnSellItems(player, inv);
    }
    private void sellContents(Player player, Inventory inv) {
        double total = 0;
        for (int i = 0; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            String path = findItemPath(item.getType());
            if (path == null || !getConfig().getBoolean(path + ".sellable", true)) continue;
            total += getConfig().getDouble(path + ".sell", 0) * item.getAmount();
        }
        if (total <= 0) { player.sendMessage(color("&cThere are no sellable items in the menu.")); return; }
        for (int i = 0; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            String path = findItemPath(item.getType());
            if (path != null && getConfig().getBoolean(path + ".sellable", true)) inv.setItem(i, null);
        }
        setBalance(player.getUniqueId(), balance(player.getUniqueId()) + total);
        saveBalances();
        player.sendMessage(color("&aSold items for " + money(total) + "."));
        player.closeInventory();
    }
    private void returnSellItems(Player player, Inventory inv) {
        for (int i = 0; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }
}

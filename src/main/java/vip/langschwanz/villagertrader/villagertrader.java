package vip.langschwanz.villagertrader;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class villagertrader extends JavaPlugin implements Listener {

    private final Map<UUID, Villager> editingVillagers = new HashMap<>();
    private boolean tradingEnabled = true;

    @Override
    public void onEnable() {
        // Plugin startup logic
        Bukkit.getPluginManager().registerEvents(this, this);

        // Register the command using Paper's method
        this.getServer().getCommandMap().register("toggletrading", new org.bukkit.command.Command("toggletrading") {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                if (!sender.hasPermission("villagertrader.toggle")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }

                tradingEnabled = !tradingEnabled;
                sender.sendMessage(ChatColor.GREEN + "Trading has been " + (tradingEnabled ? "enabled" : "disabled") + ".");
                return true;
            }
        });

        getLogger().info("VillagerTrader enabled!");
    }

    @Override
    public @NotNull Path getDataPath() {
        return super.getDataPath();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("VillagerTrader disabled!");
    }

    @EventHandler
    public void onPlayerShiftRightClickVillager(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        if (!tradingEnabled) {
            return;
        }

        event.setCancelled(true);
        Villager villager = (Villager) event.getRightClicked();

        Inventory gui = Bukkit.createInventory(null, 9 * 6, "Edit Villager Trades");

        // Create grey glass pane for spacing
        ItemStack glassPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = glassPane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glassPane.setItemMeta(meta);
        }

        // Fill GUI with current trades if any - 2 trades per row
        List<MerchantRecipe> recipes = villager.getRecipes();
        for (int tradeIndex = 0; tradeIndex < recipes.size() && tradeIndex < 12; tradeIndex++) {
            MerchantRecipe recipe = recipes.get(tradeIndex);

            // Calculate position: 2 trades per row with new layout
            int row = tradeIndex / 2;
            int posInRow = tradeIndex % 2;

            int baseSlot;
            if (posInRow == 0) {
                // First trade: slots 1, 2, 3 (after first glass pane)
                baseSlot = row * 9;
                List<ItemStack> ingredients = recipe.getIngredients();
                gui.setItem(baseSlot + 1, ingredients.get(0)); // First ingredient
                if (ingredients.size() > 1) {
                    gui.setItem(baseSlot + 2, ingredients.get(1)); // Second ingredient
                }
                gui.setItem(baseSlot + 3, recipe.getResult()); // Result
            } else {
                // Second trade: slots 5, 6, 7 (after middle glass pane)
                baseSlot = row * 9;
                List<ItemStack> ingredients = recipe.getIngredients();
                gui.setItem(baseSlot + 5, ingredients.get(0)); // First ingredient
                if (ingredients.size() > 1) {
                    gui.setItem(baseSlot + 6, ingredients.get(1)); // Second ingredient
                }
                gui.setItem(baseSlot + 7, recipe.getResult()); // Result
            }
        }

        // Fill all glass pane positions (spacing | trade | spacing | trade | spacing)
        for (int row = 0; row < 6; row++) {
            gui.setItem(row * 9 + 0, glassPane); // Left spacing
            gui.setItem(row * 9 + 4, glassPane); // Middle spacing (between trades)
            gui.setItem(row * 9 + 8, glassPane); // Right spacing
        }

        editingVillagers.put(player.getUniqueId(), villager);
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!editingVillagers.containsKey(player.getUniqueId())) return;

        if (!event.getView().getTitle().equals("Edit Villager Trades")) return;

        // Check if the click is in the GUI inventory (top) or player inventory (bottom)
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            // Clicking in player inventory - always allow
            event.setCancelled(false);
            return;
        }

        // Debug logging for GUI inventory clicks only
        int slot = event.getSlot();
        int rowPosition = slot % 9;

        // Check if clicked slot is a spacing slot in the GUI (slots 0, 4, and 8 of each row)
        if (rowPosition == 0 || rowPosition == 4 || rowPosition == 8) { // Glass pane slots
            event.setCancelled(true); // Prevent interaction with glass panes
            player.sendMessage(ChatColor.RED + "Cannot place items in the spacing slots!");
            return;
        }

        // Allow editing for other GUI slots
        event.setCancelled(false);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (!editingVillagers.containsKey(player.getUniqueId())) return;

        if (!event.getView().getTitle().equals("Edit Villager Trades")) return;

        // Save the trades when inventory is closed - 2 trades per row
        Inventory inv = event.getInventory();
        List<MerchantRecipe> newTrades = new ArrayList<>();

        // Process each row (6 rows total)
        for (int row = 0; row < 6; row++) {
            int baseSlot = row * 9;

            // First trade in the row (slots 1, 2, 3)
            ItemStack ingredient1 = inv.getItem(baseSlot + 1);
            ItemStack ingredient2 = inv.getItem(baseSlot + 2);
            ItemStack result = inv.getItem(baseSlot + 3);
            if (ingredient1 != null && result != null && !ingredient1.getType().isAir() && !result.getType().isAir()) {
                MerchantRecipe recipe = new MerchantRecipe(result, 10); // Normal max uses
                recipe.addIngredient(ingredient1);
                if (ingredient2 != null && !ingredient2.getType().isAir()) {
                    recipe.addIngredient(ingredient2);
                }
                recipe.setExperienceReward(true); // Enable experience rewards
                newTrades.add(recipe);
            }

            // Second trade in the row (slots 5, 6, 7)
            ItemStack ingredient1_2 = inv.getItem(baseSlot + 5);
            ItemStack ingredient2_2 = inv.getItem(baseSlot + 6);
            ItemStack result_2 = inv.getItem(baseSlot + 7);
            if (ingredient1_2 != null && result_2 != null && !ingredient1_2.getType().isAir() && !result_2.getType().isAir()) {
                MerchantRecipe recipe = new MerchantRecipe(result_2, 10); // Normal max uses
                recipe.addIngredient(ingredient1_2);
                if (ingredient2_2 != null && !ingredient2_2.getType().isAir()) {
                    recipe.addIngredient(ingredient2_2);
                }
                recipe.setExperienceReward(true); // Enable experience rewards
                newTrades.add(recipe);
            }
        }

        Villager villager = editingVillagers.remove(player.getUniqueId());
        if (villager != null) {
            // Set the new trades
            villager.setRecipes(newTrades);
            // Lock the villager to prevent trades from being reset
            villager.setVillagerLevel(5);
            villager.setVillagerExperience(250);
            player.sendMessage(ChatColor.GREEN + "Villager trades have been set and locked to prevent accidental resets!");
        }
    }
}

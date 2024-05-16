package de.stylelabor.dev.playercountryinfo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;


class ReloadCommand implements CommandExecutor {

    private final PlayerCountryInfo plugin;

    public ReloadCommand(PlayerCountryInfo plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        plugin.reloadConfig();
        plugin.loadCountryCodes();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String countryCode = plugin.getCountryCode(player);
            if (countryCode != null) {
                plugin.getPlayerCountryCodes().put(player.getName(), countryCode);
                String prefix = plugin.getPrefix(player);
                String format = plugin.getConfig().getString("tabFormat", "%prefix%%name% [%countryCode%]");
                format = format.replace("%prefix%", prefix.isEmpty() ? "" : prefix + " ")
                        .replace("%name%", player.getName())
                        .replace("%countryCode%", countryCode);
                player.setPlayerListName(format);
            }
        }
        sender.sendMessage(ChatColor.GREEN + "PlayerCountryInfo has been reloaded.");
        return true;
    }
}

public final class PlayerCountryInfo extends JavaPlugin implements Listener {

    private static final Logger LOGGER = Logger.getLogger(PlayerCountryInfo.class.getName());
    private final Map<String, String> playerCountryCodes = new HashMap<>();
    private final Map<String, String> countryCodes = new HashMap<>();

    public Map<String, String> getPlayerCountryCodes() {
        return this.playerCountryCodes;
    }

    @SuppressWarnings("unused")
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        // Register the reload command
        Objects.requireNonNull(getCommand("pci-reload")).setExecutor(new ReloadCommand(this));
        saveDefaultConfig();
        saveResource("data-country.json", false);
        if (new File(getDataFolder(), "data-country.json").exists()) {
            LOGGER.log(Level.INFO, "data-country.json has been extracted successfully");
        } else {
            LOGGER.log(Level.SEVERE, "Failed to extract data-country.json");
        }
        loadCountryCodes();

        // Initialize bStats
        int pluginId = 21948; // Replace with your plugin's bStats ID
        Metrics metrics = new Metrics(this, pluginId);

        // Schedule a repeating task that updates the tab list for all online players every 5 seconds
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String countryCode = getCountryCode(player);
                if (countryCode != null) {
                    String prefix = getPrefix(player);
                    String prefixWithSpace = prefix.isEmpty() ? "" : prefix + " "; // Add space to prefix if it's not empty

                    // Set the player list name (displayed in the tab list)
                    String tabFormat = getConfig().getString("tabFormat", "%prefix%%name% [%countryCode%]");
                    tabFormat = tabFormat.replace("%prefix%", prefixWithSpace)
                            .replace("%name%", player.getName())
                            .replace("%countryCode%", countryCode);
                    player.setPlayerListName(tabFormat);
                }
            }
        }, 0L, 600); // 0L is the delay before the first execution (in ticks), 100L is the period (in ticks)
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String countryCode = playerCountryCodes.get(player.getName());
        if (countryCode != null) {
            String prefix = getPrefix(player);
            String strippedPrefix = ChatColor.stripColor(prefix); // Strip color codes from the prefix
            String strippedPrefixWithSpace = strippedPrefix.isEmpty() ? "" : strippedPrefix + " "; // Add space to stripped prefix if it's not empty

            String format = getConfig().getString("customJoinFormat", "%prefix%%name% %countryCode% -> %message%");
            format = format.replace("%prefix%", strippedPrefixWithSpace)
                    .replace("%name%", player.getName())
                    .replace("%countryCode%", countryCode)
                    .replace("%message%", "has left the game"); // Change the message to "has left the game"
            event.setQuitMessage(ChatColor.YELLOW + format); // Set the custom quit message
        }
    }

    public void loadCountryCodes() {
        try {
            // Load the JSON file
            File file = new File(getDataFolder(), "data-country.json");
            if (!file.exists()) {
                throw new FileNotFoundException("data-country.json not found");
            }

            LOGGER.log(Level.INFO, "Loading country codes from " + file.getAbsolutePath());

            // Parse the JSON file into a list of Country objects
            ObjectMapper objectMapper = new ObjectMapper();
            List<Country> countries = objectMapper.readValue(file, new TypeReference<List<Country>>() {});

            // Convert the list of Country objects into a map
            for (Country country : countries) {
                countryCodes.put(country.getName(), country.getCode());
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "An error occurred while loading the country codes", e);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String countryCode = getCountryCode(player);
        if (countryCode != null) {
            playerCountryCodes.put(player.getName(), countryCode);
            String prefix = getPrefix(player);
            String strippedPrefix = ChatColor.stripColor(prefix); // Strip color codes from the prefix
            String prefixWithSpace = prefix.isEmpty() ? "" : prefix + " "; // Add space to prefix if it's not empty
            String strippedPrefixWithSpace = strippedPrefix.isEmpty() ? "" : strippedPrefix + " "; // Add space to stripped prefix if it's not empty

            String format = getConfig().getString("customJoinFormat", "%prefix%%name% %countryCode% -> %message%");
            format = format.replace("%prefix%", strippedPrefixWithSpace)
                    .replace("%name%", player.getName())
                    .replace("%countryCode%", countryCode)
                    .replace("%message%", "has joined the game");
            event.setJoinMessage(null); // Disable the default join message
            Bukkit.broadcastMessage(ChatColor.YELLOW + format); // Send the custom join message

            // Set the player list name (displayed in the tab list)
            String tabFormat = getConfig().getString("tabFormat", "%prefix%%name% [%countryCode%]");
            tabFormat = tabFormat.replace("%prefix%", prefixWithSpace)
                    .replace("%name%", player.getName())
                    .replace("%countryCode%", countryCode);
            player.setPlayerListName(tabFormat);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String countryCode = playerCountryCodes.get(player.getName());
        if (countryCode != null) {
            String prefix = getPrefix(player);
            String format = getConfig().getString("chatFormat", "%prefix%%name% %countryCode% -> %message%");
            format = format.replace("%prefix%", prefix.isEmpty() ? "" : prefix + " ")
                    .replace("%name%", player.getName())
                    .replace("%countryCode%", countryCode)
                    .replace("%message%", event.getMessage());
            event.setFormat(format);
        }
    }

    public String getCountryCode(Player player) {
        // Get the player's IP address
        String ip = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();

        // If the IP address is a loopback address, return a default country code
        if (ip.equals("127.0.0.1")) {
            return getConfig().getString("defaultCountryCode", "LOCAL");
        }

        try {
            // Use the HackerTarget API to get the country of the IP address
            URL url = new URL("https://api.hackertarget.com/ipgeo/?q=" + ip);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            String countryName = "";

            while ((line = reader.readLine()) != null) {
                if (line.contains("Country:")) {
                    countryName = line.split(":")[1].trim();
                    break;
                }
            }

            // Look up the country code in the map
            String countryCode = countryCodes.get(countryName);
            if (countryCode == null) {
                LOGGER.log(Level.WARNING, "No country code found for " + countryName);
                return null;
            }

            LOGGER.log(Level.INFO, "Retrieved country code for " + player.getName() + ": " + countryCode);
            return countryCode;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "An error occurred while getting the country of the IP address", e);
        }

        return null;
    }

    public String getPrefix(Player player) {
        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            String prefix = user.getCachedData().getMetaData().getPrefix();
            if (prefix != null) {
                return ChatColor.translateAlternateColorCodes('&', prefix);
            }
        }
        return "";
    }
}
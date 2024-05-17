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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
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
    private FileConfiguration playersConfig;

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

        // Create the players.yml file if it doesn't exist
        File playersFile = new File(getDataFolder(), "players.yml");
        if (!playersFile.exists()) {
            try {
                boolean fileCreated = playersFile.createNewFile();
                if (!fileCreated) {
                    LOGGER.log(Level.WARNING, "players.yml already exists");
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not create players.yml file", e);
            }
        }

        // Load the players.yml file into the playersConfig object
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);

        // Check if the automatic tab list update is enabled
        if (getConfig().getBoolean("enableTabListUpdate", true)) {
            // Schedule a repeating task that updates the tab list for all online players
            long interval = getConfig().getLong("tabListUpdateInterval", 5) * 20; // Convert seconds to ticks
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String countryCode = getCountryCode(player);
                    if (countryCode != null) {
                        String prefix = getPrefix(player);
                        String prefixWithSpace = prefix.isEmpty() ? "" : prefix + " "; // Add space to prefix if it's not empty

                        // Get the player's death count
                        int deaths = getDeaths(player);

                        // Set the player list name (displayed in the tab list)
                        String tabFormat = getConfig().getString("tabFormat", "%prefix%%name% [%countryCode%] %deaths%");
                        tabFormat = tabFormat.replace("%prefix%", prefixWithSpace)
                                .replace("%name%", player.getName())
                                .replace("%countryCode%", countryCode)
                                .replace("%deaths%", String.valueOf(deaths)); // Replace the %deaths% placeholder
                        player.setPlayerListName(tabFormat);
                    }
                }
            }, 0L, interval); // 0L is the delay before the first execution (in ticks), interval is the period (in ticks)
        }
    }

    public int getDeaths(Player player) {
        String uuid = player.getUniqueId().toString();

        // Check the players.yml file first
        if (playersConfig.contains(uuid + ".deaths")) {
            return playersConfig.getInt(uuid + ".deaths");
        }

        return 0; // Return 0 if the player is not in the players.yml file
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String uuid = player.getUniqueId().toString();

        // Get the current death count from the players.yml file
        int deaths = getDeaths(player);

        // Increment the death count
        deaths++;

        // Update the death count in the players.yml file
        playersConfig.set(uuid + ".deaths", deaths);

        // Save the players.yml file
        try {
            playersConfig.save(new File(getDataFolder(), "players.yml"));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not save players.yml file", e);
        }
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

            // Get the player's death count
            int deaths = getDeaths(player);
            System.out.println("Player " + player.getName() + " has " + deaths + " deaths."); // Debug message

            // Set the player list name (displayed in the tab list)
            String tabFormat = getConfig().getString("tabFormat", "%prefix%%name% [%countryCode%] %deaths%");
            tabFormat = tabFormat.replace("%prefix%", prefixWithSpace)
                    .replace("%name%", player.getName())
                    .replace("%countryCode%", countryCode)
                    .replace("%deaths%", String.valueOf(deaths)); // Replace the %deaths% placeholder
            player.setPlayerListName(tabFormat);

            // Write the player's information to the players.yml file
            String playerKey = player.getUniqueId().toString();
            playersConfig.set(playerKey + ".ip", Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress());
            playersConfig.set(playerKey + ".countryCode", countryCode);
            playersConfig.set(playerKey + ".uuid", playerKey);
            playersConfig.set(playerKey + ".name", player.getName()); // Save the player's name

            // Format the current date and time as a string
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String lastJoin = dateFormat.format(new Date());
            playersConfig.set(playerKey + ".lastJoin", lastJoin);

            // Save the players.yml file
            try {
                playersConfig.save(new File(getDataFolder(), "players.yml"));
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not save players.yml file", e);
            }
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
        // Get the player's UUID
        String uuid = player.getUniqueId().toString();

        // Check the players.yml file first
        if (playersConfig.contains(uuid + ".countryCode")) {
            return playersConfig.getString(uuid + ".countryCode");
        }

        // If the country code is not in the players.yml file, use the HackerTarget API to get the country of the IP address
        String ip = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();

        // Remove the port from the IP address, if present
        if (ip.contains(":")) {
            ip = ip.substring(0, ip.indexOf(":"));
        }

        // If the IP address is a loopback address, return a default country code
        if (ip.equals("127.0.0.1")) {
            return getConfig().getString("defaultCountryCode", "LOCAL");
        }

        try {
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
                LOGGER.log(Level.WARNING, "No country code found for " + countryName + " for player " + player.getName());
                countryCode = "XX"; // Use "XX" as a default country code
            }

            // Add the country code to the players.yml file
            playersConfig.set(uuid + ".countryCode", countryCode);
            try {
                playersConfig.save(new File(getDataFolder(), "players.yml"));
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not save players.yml file", e);
            }

            if (getConfig().getBoolean("enableDebugMessages", true)) {
                LOGGER.log(Level.INFO, "Retrieved country code for " + player.getName() + ": " + countryCode);
            }

            return countryCode;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "An error occurred while getting the country of the IP address", e);
        }

        return "XX"; // Return "XX" as a default country code if an exception occurs
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
package de.stylelabor.dev.playercountryinfo;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public final class PlayerCountryInfo extends JavaPlugin {

    private File playerFile;
    private FileConfiguration playerConfig;
    private static final Logger LOGGER = Logger.getLogger(PlayerCountryInfo.class.getName());

    @Override
    public void onEnable() {
        loadPlayerFile();
        new CountryPlaceholder(this).register();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public void loadPlayerFile() {
        playerFile = new File(getDataFolder(), "players.yml");
        if (!playerFile.exists()) {
            if (!playerFile.getParentFile().mkdirs()) {
                LOGGER.log(Level.SEVERE, "Could not create directories for players.yml");
                return;
            }
            saveResource("players.yml", false);
        }

        playerConfig = new YamlConfiguration();
        try {
            playerConfig.load(playerFile);
        } catch (IOException | InvalidConfigurationException e) {
            LOGGER.log(Level.SEVERE, "An error occurred while loading the players.yml file", e);
        }
    }

    public void savePlayerInfo(Player player, String country) {
        String uuid = player.getUniqueId().toString();
        playerConfig.set(uuid + ".name", player.getName());
        playerConfig.set(uuid + ".country", country);
        try {
            playerConfig.save(playerFile);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not save player info to players.yml", e);
        }
    }

    public static class CountryPlaceholder extends PlaceholderExpansion {

        private final PlayerCountryInfo plugin;

        public CountryPlaceholder(PlayerCountryInfo plugin){
            this.plugin = plugin;
        }

        @Override
        public boolean persist(){
            return true;
        }

        @Override
        public boolean canRegister(){
            return true;
        }

        @Override
        public @NotNull String getAuthor(){
            return plugin.getDescription().getAuthors().toString();
        }

        @Override
        public @NotNull String getIdentifier(){
            return "countrycode";
        }

        @Override
        public @NotNull String getVersion(){
            return plugin.getDescription().getVersion();
        }

        @SuppressWarnings("ExtractMethodRecommender")
        @Override
        public String onPlaceholderRequest(Player player, @NotNull String identifier){

            if(player == null){
                return "";
            }

            // Get the player's IP address
            String ip = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();

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

                // Convert the country name to its corresponding country code
                String countryCode = new Locale("", countryName).getCountry();

                // Save the player's information
                plugin.savePlayerInfo(player, countryCode);

                return countryCode;

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "An error occurred while getting the country of the IP address", e);
            }

            return null;
        }
    }
}
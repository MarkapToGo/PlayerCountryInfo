package de.stylelabor.dev.playercountryinfo;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerCountryInfo extends JavaPlugin implements Listener {

    private static final Logger LOGGER = Logger.getLogger(PlayerCountryInfo.class.getName());
    private final Map<String, String> playerCountryCodes = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String countryCode = getCountryCode(player);
        if (countryCode != null) {
            playerCountryCodes.put(player.getName(), countryCode);
            player.setPlayerListName(player.getName() + " (" + countryCode + ")");
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String countryCode = playerCountryCodes.get(player.getName());
        if (countryCode != null) {
            String format = String.format("<%s (%s)> %s", player.getName(), countryCode, event.getMessage());
            event.setFormat(format);
        }
    }

    private String getCountryCode(Player player) {
        // Get the player's IP address
        String ip = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();

        // If the IP address is a loopback address, return a default country code
        if (ip.equals("127.0.0.1")) {
            return "LOCAL"; // Replace "US" with your desired default country code
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

            // Convert the country name to its corresponding country code
            String countryCode = new Locale("", countryName).getCountry();
            LOGGER.log(Level.INFO, "Retrieved country code for " + player.getName() + ": " + countryCode);
            return countryCode;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "An error occurred while getting the country of the IP address", e);
        }

        return null;
    }

}
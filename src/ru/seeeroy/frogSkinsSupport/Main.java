package ru.seeeroy.frogSkinsSupport;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.logging.Logger;
import net.md_5.bungee.api.ChatColor;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.VersionProvider;
import net.skinsrestorer.api.exception.DataRequestException;
import net.skinsrestorer.api.exception.MineSkinException;
import net.skinsrestorer.api.property.InputDataResult;
import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.api.storage.PlayerStorage;
import net.skinsrestorer.api.storage.SkinStorage;

public class Main extends JavaPlugin implements Listener {
	String skinsApiUrl = "https://skins.froglauncher.ru/sessionserver/textures/";
	private SkinsRestorer skinsRestorerAPI;
	private final Logger logger = getLogger();

	@Override
	public void onDisable() {
		// Nothing here
	}

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);

		logger.info(ChatColor.AQUA + "Hooking with SkinsRestorer API");

		if (!VersionProvider.isCompatibleWith("15")) {
			logger.info("This plugin was made for SkinsRestorer v15, but " + VersionProvider.getVersionInfo()
					+ " is installed. There may be errors!");
		}

		// Соединяемся с SkinsRestorer
		skinsRestorerAPI = SkinsRestorerProvider.get();
		logger.info(ChatColor.AQUA + "Done!");
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		this.updatePlayerSkin(p);
	}

	public void updatePlayerSkin(Player p) {
		// Получаем данные из API
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) new URL(skinsApiUrl + p.getName()).openConnection();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		int responseCode = 0;
		try {
			responseCode = connection.getResponseCode();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// Если код 204, то скина нет
		if(responseCode == 204) {
			getLogger().info("Skin for " + ChatColor.AQUA + p.getName() + ChatColor.RESET + " not found");
			return;
		}
		
		InputStream inputStream = null;
		if (200 <= responseCode && responseCode <= 299) {
			try {
				inputStream = connection.getInputStream();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			inputStream = connection.getErrorStream();
		}

		BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));

		// Парсим ответ сервера
		StringBuilder response = new StringBuilder();
		String currentLine;

		try {
			while ((currentLine = in.readLine()) != null)
				response.append(currentLine);
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Парсим JSON
		String resp = response.toString();
		JsonParser parser = new JsonParser();
		JsonObject mainObject = parser.parse(resp).getAsJsonObject();

		// Ищем value и signature скина
		String skinData = mainObject.get("value").getAsString();
		String skinSignature = mainObject.get("signature").getAsString();
		
		if(skinData.isEmpty() || skinSignature.isEmpty()) {
			getLogger().info("Skin for " + ChatColor.AQUA + p.getName() + ChatColor.RESET + " not found");
			return;
		}
		getLogger().info("Loading skin for " + ChatColor.AQUA + p.getName());
		
		String skin = p.getName();
		try {
			// Загружаем скин с помощью SkinsRestorerAPI
			SkinStorage skinStorage = skinsRestorerAPI.getSkinStorage();
			skinStorage.setCustomSkinData(p.getName(), SkinProperty.of(skinData, skinSignature));
			Optional<InputDataResult> result = skinStorage.findOrCreateSkinData(skin);
			PlayerStorage playerStorage = skinsRestorerAPI.getPlayerStorage();
			playerStorage.setSkinIdOfPlayer(p.getUniqueId(), result.get().getIdentifier());
			skinsRestorerAPI.getSkinApplier(Player.class).applySkin(p);
			getLogger().info("Successfully loaded skin for " + ChatColor.AQUA + p.getName());
		} catch (DataRequestException | MineSkinException e) {
			((Throwable) e).printStackTrace();
		}
	}
}
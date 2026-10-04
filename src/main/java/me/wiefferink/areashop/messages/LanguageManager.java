package me.wiefferink.areashop.messages;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads language files with MiniMessage messages and provides them by key.
 * Language files that still use the old markup are converted while loading.
 */
public class LanguageManager implements MessageProvider {

	/** Key in a language file that marks it as using MiniMessage (not a message itself) */
	public static final String FORMAT_KEY = "minimessage";

	private final JavaPlugin plugin;
	private final File languageFolder;
	private final String jarLanguagePath;
	private final String chatPrefix;
	private Map<String, String> currentLanguage;
	private Map<String, String> defaultLanguage;

	/**
	 * Constructor.
	 * @param plugin              The plugin creating this LanguageManager (used for logging and finding the language files in the jar)
	 * @param jarLanguagePath     The path in the jar to the folder with the language files
	 * @param currentLanguageName The name of the language that should be active (without '.yml')
	 * @param defaultLanguageName The name of the language that is used for messages missing in the current language
	 * @param chatPrefix          The chat prefix for {@link Message#prefix()}, MiniMessage or old markup
	 */
	public LanguageManager(JavaPlugin plugin, String jarLanguagePath, String currentLanguageName, String defaultLanguageName, List<String> chatPrefix) {
		this.plugin = plugin;
		this.jarLanguagePath = jarLanguagePath;
		this.chatPrefix = chatPrefix == null ? "" : LegacyMarkup.convertIfLegacy(chatPrefix);
		this.languageFolder = new File(plugin.getDataFolder() + File.separator + jarLanguagePath);

		Message.init(this, plugin.getLogger());
		saveDefaults();
		currentLanguage = loadLanguage(currentLanguageName);
		if(defaultLanguageName.equals(currentLanguageName)) {
			defaultLanguage = currentLanguage;
		} else {
			defaultLanguage = loadLanguage(defaultLanguageName);
		}
	}

	/**
	 * Get the MiniMessage template for a certain key.
	 * @param key The key of the message to get
	 * @return The message, empty if it does not exist
	 */
	@Override
	public String getMessage(String key) {
		String message;
		if(key.equalsIgnoreCase(Message.CHATLANGUAGEVARIABLE)) {
			message = chatPrefix;
		} else if(currentLanguage.containsKey(key)) {
			message = currentLanguage.get(key);
		} else {
			message = defaultLanguage.get(key);
		}
		if(message == null) {
			plugin.getLogger().warning("Did not find message '" + key + "' in the current or default language");
			return "";
		}
		return message;
	}

	/**
	 * Saves the default language files, overwrites existing ones.
	 */
	private void saveDefaults() {
		if(!languageFolder.exists() && !languageFolder.mkdirs()) {
			plugin.getLogger().warning("Could not create language directory: " + languageFolder.getAbsolutePath());
			return;
		}

		try(ZipFile jar = new ZipFile(new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()))) {
			Enumeration<? extends ZipEntry> entries = jar.entries();
			while(entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();

				// Filter to YAML files in the language directory
				if(!entry.isDirectory() && entry.getName().startsWith(jarLanguagePath + "/") && entry.getName().endsWith(".yml")) {
					File targetFile = new File(languageFolder, entry.getName().substring(entry.getName().lastIndexOf('/') + 1));
					try(
							InputStream input = jar.getInputStream(entry);
							OutputStream output = new FileOutputStream(targetFile)
					) {
						input.transferTo(output);
					} catch(IOException e) {
						plugin.getLogger().warning("Something went wrong saving a default language file: " + targetFile.getAbsolutePath());
					}
				}
			}
		} catch(URISyntaxException | IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Failed to read the jar file to save the language files:", e);
		}
	}

	/**
	 * Loads the specified language.
	 * @param name The language to load
	 * @return Map with the MiniMessage messages loaded from the file
	 */
	private Map<String, String> loadLanguage(String name) {
		Map<String, String> result = new HashMap<>();
		File file = new File(languageFolder, name + ".yml");
		try(InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
			// Detect empty language files, happens when the YAML parsers prints an exception (it does return an empty YamlConfiguration though)
			YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
			if(yaml.getKeys(false).isEmpty()) {
				plugin.getLogger().warning("Language file " + name + ".yml has zero messages.");
				return result;
			}

			boolean miniMessage = yaml.getBoolean(FORMAT_KEY, false);
			boolean converted = false;
			for(String messageKey : yaml.getKeys(false)) {
				if(messageKey.equals(FORMAT_KEY)) {
					continue;
				}
				List<String> lines = yaml.isList(messageKey) ? yaml.getStringList(messageKey) : List.of(String.valueOf(yaml.getString(messageKey, "")));
				if(miniMessage) {
					result.put(messageKey, String.join("", lines));
				} else {
					result.put(messageKey, LegacyMarkup.convertIfLegacy(lines));
					converted |= LegacyMarkup.isLegacy(lines);
				}
			}
			if(converted) {
				plugin.getLogger().info("Language file " + name + ".yml uses the old formatting, it has been converted to MiniMessage in memory. "
						+ "Copy a current language file (like EN.yml) and adapt it to update, see https://docs.advntr.dev/minimessage/format.html");
			}
		} catch(IOException e) {
			plugin.getLogger().warning("Could not load language file: " + file.getAbsolutePath());
		}
		return result;
	}
}

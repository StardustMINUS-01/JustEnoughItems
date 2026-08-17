package mezz.jei.gui.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import mezz.jei.common.config.file.JsonArrayFileHelper;
import mezz.jei.common.util.DeduplicatingRunner;
import mezz.jei.common.util.ServerConfigPathUtil;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FavoriteRecipeConfig {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Duration SAVE_DELAY_TIME = Duration.ofSeconds(5);
	private static final int VERSION = 1;
	private final Path jeiConfigurationDir;
	private final DeduplicatingRunner delayedSave = new DeduplicatingRunner(SAVE_DELAY_TIME);

	public FavoriteRecipeConfig(Path jeiConfigurationDir) {
		this.jeiConfigurationDir = jeiConfigurationDir;
	}

	public FavoriteRecipeStore loadFavorites() {
		Optional<Path> path = getPath();
		if (path.isEmpty() || !Files.exists(path.get())) {
			return new FavoriteRecipeStore();
		}

		JsonArray entries = readEntries(path.get());
		if (entries == null) {
			LOGGER.error("Failed to load favorite recipes from file {}: missing or invalid content", path.get());
			return new FavoriteRecipeStore();
		}
		FavoriteRecipeStore store = FavoriteRecipeJsonSerializer.deserializeStore(entries);
		return store;
	}

	private JsonArray readEntries(Path path) {
		try {
			List<JsonElement> elements = JsonArrayFileHelper.read(path, VERSION, (element, message) ->
				LOGGER.error("Failed to load favorite recipes from file {}: {}", path, message)
			);
			JsonArray entries = new JsonArray();
			for (JsonElement element : elements) {
				entries.add(element);
			}
			return entries;
		} catch (IOException | JsonSyntaxException e) {
			LOGGER.error("Failed to load favorite recipes from file {}", path, e);
			return null;
		}
	}

	public void saveFavorites(FavoriteRecipeStore store) {
		List<JsonObject> elements = FavoriteRecipeJsonSerializer.serializeStore(store);
		getPath()
			.ifPresent(path -> delayedSave.run(() -> save(path, elements)));
	}

	private void save(Path path, List<JsonObject> elements) {
		try {
			JsonArrayFileHelper.write(path, VERSION, new ArrayList<JsonElement>(elements));
		} catch (IOException e) {
			LOGGER.error("Failed to save favorite recipes to file {}", path, e);
		}
	}

	private Optional<Path> getPath() {
		return ServerConfigPathUtil.getWorldPath(jeiConfigurationDir)
			.flatMap(configPath -> {
				try {
					Files.createDirectories(configPath);
				} catch (IOException e) {
					LOGGER.error("Unable to create favorite recipe config folder: {}", configPath);
					return Optional.empty();
				}
				return Optional.of(configPath.resolve("favorites.json"));
			});
	}

}

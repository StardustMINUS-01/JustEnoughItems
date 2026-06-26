package mezz.jei.gui.config;

import mezz.jei.common.util.ServerConfigPathUtil;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class FavoriteRecipeConfig {
	private static final Logger LOGGER = LogManager.getLogger();
	private final Path jeiConfigurationDir;

	public FavoriteRecipeConfig(Path jeiConfigurationDir) {
		this.jeiConfigurationDir = jeiConfigurationDir;
	}

	public FavoriteRecipeStore loadFavorites() {
		Optional<Path> path = getPath();
		if (path.isEmpty() || !Files.exists(path.get())) {
			return new FavoriteRecipeStore();
		}

		try {
			List<String> lines = Files.readAllLines(path.get());
			return FavoriteRecipeConfigSerializer.deserializeStore(lines);
		} catch (IOException e) {
			LOGGER.error("Failed to load favorite recipes from file {}", path.get(), e);
			return new FavoriteRecipeStore();
		}
	}

	public void saveFavorites(FavoriteRecipeStore store) {
		getPath()
			.ifPresent(path -> {
				List<String> lines = FavoriteRecipeConfigSerializer.serializeStore(store);
				try {
					Files.write(path, lines);
					LOGGER.debug("Saved favorite recipes to file {}", path);
				} catch (IOException e) {
					LOGGER.error("Failed to save favorite recipes to file {}", path, e);
				}
			});
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
				return Optional.of(configPath.resolve("favorite-recipes.ini"));
			});
	}
}

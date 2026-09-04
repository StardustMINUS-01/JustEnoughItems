package mezz.jei.gui.config;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.file.JsonArrayFileHelper;
import mezz.jei.common.util.DeduplicatingRunner;
import mezz.jei.common.util.ServerConfigPathUtil;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class FavoriteRecipeConfig {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Duration SAVE_DELAY_TIME = Duration.ofSeconds(5);
	private static final int VERSION = 2;

	private final Path jeiConfigurationDir;
	private final Codec<FavoriteRecipeStore.Entry> entryCodec;
	private final RegistryOps<JsonElement> registryOps;
	private final DeduplicatingRunner delayedSave = new DeduplicatingRunner(SAVE_DELAY_TIME);

	public FavoriteRecipeConfig(
		Path jeiConfigurationDir,
		ICodecHelper codecHelper,
		IIngredientManager ingredientManager,
		RegistryAccess registryAccess
	) {
		this.jeiConfigurationDir = jeiConfigurationDir;
		this.entryCodec = FavoriteRecipeJsonSerializer.create(codecHelper, ingredientManager);
		this.registryOps = registryAccess.createSerializationContext(JsonOps.INSTANCE);
	}

	public FavoriteRecipeStore loadFavorites() {
		Optional<Path> optionalPath = getPath();
		if (optionalPath.isEmpty() || !Files.exists(optionalPath.get())) {
			return new FavoriteRecipeStore();
		}

		Path path = optionalPath.get();
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			List<FavoriteRecipeStore.Entry> entries = JsonArrayFileHelper.read(
				reader,
				VERSION,
				entryCodec,
				registryOps,
				(element, error) -> LOGGER.error("Encountered an error when loading favorite recipes from file {}\n{}\n{}", path, element, error),
				(element, exception) -> LOGGER.error("Encountered an exception when loading favorite recipes from file {}\n{}", path, element, exception)
			);
			FavoriteRecipeStore store = new FavoriteRecipeStore();
			store.setFavorites(entries);
			LOGGER.debug("Loaded favorite recipes from file: {}", path);
			return store;
		} catch (RuntimeException | IOException e) {
			LOGGER.error("Failed to load favorite recipes from file {}", path, e);
			return new FavoriteRecipeStore();
		}
	}

	public void saveFavorites(FavoriteRecipeStore store) {
		List<FavoriteRecipeStore.Entry> entries = store.entries();
		getPath().ifPresent(path -> delayedSave.run(() -> save(path, entries)));
	}

	private void save(Path path, List<FavoriteRecipeStore.Entry> entries) {
		try {
			JsonArrayFileHelper.write(
				path,
				VERSION,
				entries,
				entryCodec,
				registryOps,
				error -> LOGGER.error("Encountered an error when saving favorite recipes to file {}\n{}", path, error),
				(entry, exception) -> LOGGER.error("Encountered an exception when saving favorite recipes to file {}\n{}", path, entry, exception)
			);
			LOGGER.debug("Saved favorite recipes to file {}", path);
		} catch (RuntimeException | IOException e) {
			LOGGER.error("Failed to save favorite recipes to file {}", path, e);
		}
	}

	private Optional<Path> getPath() {
		return ServerConfigPathUtil.getWorldPath(jeiConfigurationDir)
			.flatMap(configPath -> {
				try {
					Files.createDirectories(configPath);
				} catch (IOException e) {
					LOGGER.error("Unable to create favorite recipe config folder: {}", configPath, e);
					return Optional.empty();
				}
				return Optional.of(configPath.resolve("favorites.json"));
			});
	}
}

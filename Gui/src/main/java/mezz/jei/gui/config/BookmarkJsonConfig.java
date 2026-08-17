package mezz.jei.gui.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.file.JsonArrayFileHelper;
import mezz.jei.common.config.file.serializers.TypedIngredientSerializer;
import mezz.jei.common.util.DeduplicatingRunner;
import mezz.jei.common.util.PathUtil;
import mezz.jei.common.util.ServerConfigPathUtil;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.config.file.serializers.RecipeBookmarkSerializer;
import net.minecraft.core.RegistryAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class BookmarkJsonConfig implements IBookmarkConfig {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Duration SAVE_DELAY_TIME = Duration.ofSeconds(5);
	private static final int VERSION = 1;

	private static final String JSON_TYPE_STACK = "item_stack";
	private static final String JSON_TYPE_INGREDIENT = "ingredient";
	private static final String JSON_TYPE_RECIPE = "recipe";
	private static final String JSON_KEY_KIND = "kind";

	private final Path jeiConfigurationDir;
	private final DeduplicatingRunner delayedSave = new DeduplicatingRunner(SAVE_DELAY_TIME);
	private final BookmarkConfig iniBookmarkConfig;
	private @Nullable BookmarkList bookmarkList;

	public BookmarkJsonConfig(Path jeiConfigurationDir) {
		this.jeiConfigurationDir = jeiConfigurationDir;
		this.iniBookmarkConfig = new BookmarkConfig(jeiConfigurationDir);
	}

	private static Optional<Path> getPath(Path jeiConfigurationDir) {
		return ServerConfigPathUtil.getWorldPath(jeiConfigurationDir)
			.flatMap(configPath -> {
				try {
					Files.createDirectories(configPath);
				} catch (IOException e) {
					LOGGER.error("Unable to create bookmark config folder: {}", configPath, e);
					return Optional.empty();
				}
				return Optional.of(configPath.resolve("bookmarks.json"));
			});
	}

	@Override
	public void saveBookmarks(
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IGuiHelper guiHelper,
		IIngredientManager ingredientManager,
		RegistryAccess registryAccess,
		Collection<IBookmark> bookmarks
	) {
		getPath(jeiConfigurationDir)
			.ifPresent(path -> {
				delayedSave.run(() -> save(path, ingredientManager));
			});
	}

	private boolean save(Path path, IIngredientManager ingredientManager) {
		BookmarkList bookmarkList = this.bookmarkList;
		if (bookmarkList == null) {
			return false;
		}
		List<JsonElement> elements = BookmarkJsonSerializer.serialize(bookmarkList, ingredientManager);
		try {
			JsonArrayFileHelper.write(path, VERSION, elements);
			LOGGER.debug("Saved bookmarks config to file: {}", path);
			return true;
		} catch (RuntimeException | IOException e) {
			LOGGER.error("Failed to save bookmarks config to file {}", path, e);
			return false;
		}
	}

	@Override
	public void loadBookmarks(
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IGuiHelper guiHelper,
		IIngredientManager ingredientManager,
		RegistryAccess registryAccess,
		BookmarkList bookmarkList
	) {
		this.bookmarkList = bookmarkList;
		Optional<Path> jsonPath = getPath(jeiConfigurationDir);
		if (jsonPath.isEmpty()) {
			return;
		}
		Path path = jsonPath.get();
		if (Files.exists(path)) {
			List<JsonElement> elements;
			try {
				elements = JsonArrayFileHelper.read(path, VERSION, (element, error) -> {
					LOGGER.error("Encountered error when loading the bookmark config from file {}\n{}\n{}", path, element, error);
				});
			} catch (RuntimeException | IOException e) {
				LOGGER.error("Failed to load bookmarks config from file {}", path, e);
				return;
			}
			if (elements.isEmpty()) {
				return;
			}
			if (isLegacyJsonFormat(elements)) {
				// legacy bookmarks.json (type/value pairs) exists, migrate it to the new format
				iniBookmarkConfig.loadBookmarks(
					recipeManager,
					focusFactory,
					guiHelper,
					ingredientManager,
					registryAccess,
					bookmarkList
				);
				save(path, ingredientManager);
				return;
			}
			loadJson(elements, path, bookmarkList, recipeManager, focusFactory, ingredientManager);
			return;
		}
		Optional<Path> iniPath = BookmarkConfig.getPath(jeiConfigurationDir);
		if (iniPath.isPresent() && Files.exists(iniPath.get())) {
			iniBookmarkConfig.loadBookmarks(
				recipeManager,
				focusFactory,
				guiHelper,
				ingredientManager,
				registryAccess,
				bookmarkList
			);
			if (save(path, ingredientManager)) {
				Path legacyPath = iniPath.get();
				try {
					Path backupPath = legacyPath.resolveSibling(legacyPath.getFileName() + ".bak");
					PathUtil.moveAtomicReplace(legacyPath, backupPath);
					LOGGER.info("Backed up legacy bookmarks config file to '{}'", backupPath);
				} catch (IOException e) {
					LOGGER.error("Failed to back up legacy bookmarks config file '{}'", legacyPath, e);
				}
			}
		}
	}

	private static void loadJson(
		List<JsonElement> elements,
		Path path,
		BookmarkList bookmarkList,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IIngredientManager ingredientManager
	) {
		RecipeBookmarkSerializer recipeBookmarkSerializer = new RecipeBookmarkSerializer(
			recipeManager,
			focusFactory,
			new TypedIngredientSerializer(ingredientManager),
			ingredientManager
		);
		BookmarkJsonSerializer.deserialize(elements, bookmarkList, recipeBookmarkSerializer, ingredientManager);
		LOGGER.debug("Loaded bookmarks config from file: {}", path);
	}

	/**
	 * Detects the legacy bookmarks.json format that only stored a "type" and "value" per bookmark,
	 * without any group definitions or bookmark metadata.
	 */
	private static boolean isLegacyJsonFormat(List<JsonElement> elements) {
		for (JsonElement element : elements) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject json = element.getAsJsonObject();
			if (!json.has("type") || !json.get("type").isJsonPrimitive()) {
				continue;
			}
			String type = json.get("type").getAsString();
			if (JSON_TYPE_STACK.equals(type) || JSON_TYPE_INGREDIENT.equals(type)) {
				return true;
			}
			if (JSON_TYPE_RECIPE.equals(type) && !json.has(JSON_KEY_KIND)) {
				return true;
			}
		}
		return false;
	}

}

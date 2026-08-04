package mezz.jei.gui.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.file.GsonArrayFileHelper;
import mezz.jei.common.config.file.serializers.TypedIngredientSerializer;
import mezz.jei.common.util.DeduplicatingRunner;
import mezz.jei.common.util.PathUtil;
import mezz.jei.common.util.ServerConfigPathUtil;
import mezz.jei.gui.bookmarks.BookmarkFactory;
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
import java.util.List;
import java.util.Optional;

public class BookmarkJsonConfig implements IBookmarkConfig {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Duration SAVE_DELAY_TIME = Duration.ofSeconds(5);
	private static final int VERSION = 1;

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
	public boolean saveBookmarks(
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IGuiHelper guiHelper,
		IIngredientManager ingredientManager,
		RegistryAccess registryAccess,
		ICodecHelper codecHelper,
		List<IBookmark> bookmarks,
		Codec<IBookmark> bookmarkCodec
	) {
		return getPath(jeiConfigurationDir)
			.map(path -> {
				delayedSave.run(() -> save(path, ingredientManager));
				return true;
			})
			.orElse(false);
	}

	private boolean save(Path path, IIngredientManager ingredientManager) {
		BookmarkList bookmarkList = this.bookmarkList;
		if (bookmarkList == null) {
			return false;
		}
		List<JsonElement> elements = BookmarkJsonSerializer.serialize(bookmarkList, ingredientManager);
		try {
			GsonArrayFileHelper.write(path, VERSION, elements);
			LOGGER.debug("Saved bookmarks config to file: {}", path);
			return true;
		} catch (IOException e) {
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
		BookmarkList bookmarkList,
		ICodecHelper codecHelper,
		Codec<IBookmark> bookmarkCodec,
		BookmarkFactory bookmarkFactory
	) {
		this.bookmarkList = bookmarkList;
		Optional<Path> jsonPath = getPath(jeiConfigurationDir);
		if (jsonPath.isEmpty()) {
			return;
		}
		Path path = jsonPath.get();
		if (Files.exists(path)) {
			loadJson(path, bookmarkList, recipeManager, focusFactory, ingredientManager);
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
		Path path,
		BookmarkList bookmarkList,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IIngredientManager ingredientManager
	) {
		JsonArray elements = GsonArrayFileHelper.read(path, VERSION).orElse(null);
		if (elements == null) {
			LOGGER.error("Failed to load bookmarks config from file {}: missing or invalid content", path);
			return;
		}
		RecipeBookmarkSerializer recipeBookmarkSerializer = new RecipeBookmarkSerializer(
			recipeManager,
			focusFactory,
			new TypedIngredientSerializer(ingredientManager)
		);
		BookmarkJsonSerializer.deserialize(elements, bookmarkList, recipeBookmarkSerializer, ingredientManager);
		LOGGER.debug("Loaded bookmarks config from file: {}", path);
	}

}

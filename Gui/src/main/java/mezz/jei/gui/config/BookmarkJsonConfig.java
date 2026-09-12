package mezz.jei.gui.config;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.config.file.JsonArrayFileHelper;
import mezz.jei.common.transfer.RecipeTransferService;

import mezz.jei.common.util.ServerConfigPathUtil;
import mezz.jei.gui.bookmarks.BookmarkFactory;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.mezzdev.deduplicatingrunner.DeduplicatingRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Unmodifiable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class BookmarkJsonConfig implements IBookmarkConfig {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Duration SAVE_DELAY_TIME = Duration.ofSeconds(5);
	private static final int VERSION = 2;

	@SuppressWarnings("deprecation")
	private final LegacyBookmarkConfig legacyBookmarkConfig;
	private final Path jeiConfigurationDir;
	private BookmarkList bookmarkList;
	private IIngredientManager ingredientManager;
	private ICodecHelper codecHelper;
	private final DeduplicatingRunner delayedSave = new DeduplicatingRunner(SAVE_DELAY_TIME, Internal.getDelayedExecutor());

	private static Optional<Path> getPath(Path jeiConfigurationDir) {
		return ServerConfigPathUtil.getWorldPath(jeiConfigurationDir)
			.flatMap(configPath -> {
				try {
					Files.createDirectories(configPath);
				} catch (IOException e) {
					LOGGER.error("Unable to create bookmark config folder: {}", configPath, e);
					return Optional.empty();
				}
				Path path = configPath.resolve("bookmarks.json");
				return Optional.of(path);
			});
	}

	@SuppressWarnings("deprecation")
	public BookmarkJsonConfig(Path jeiConfigurationDir) {
		this.jeiConfigurationDir = jeiConfigurationDir;
		this.legacyBookmarkConfig = new LegacyBookmarkConfig(jeiConfigurationDir);
	}

	private RegistryOps<JsonElement> getRegistryOps(RegistryAccess registryAccess) {
		return registryAccess.createSerializationContext(JsonOps.INSTANCE);
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
		BookmarkList bookmarkList = this.bookmarkList;
		if (bookmarkList == null) {
			return false;
		}
		this.ingredientManager = ingredientManager;
		this.codecHelper = codecHelper;
		List<IBookmark> bookmarksSnapshot = List.copyOf(bookmarks);
		return getPath(jeiConfigurationDir)
			.map(path -> {
				delayedSave.run(() -> save(path, registryAccess, bookmarksSnapshot, bookmarkCodec));
				return true;
			})
			.orElse(false);
	}

	private boolean save(Path path, RegistryAccess registryAccess, Collection<IBookmark> bookmarks, Codec<IBookmark> bookmarkCodec) {
		Codec<BookmarkConfigEntry> entryCodec = BookmarkConfigEntryCodec.create(codecHelper, ingredientManager, bookmarkCodec);
		RegistryOps<JsonElement> registryOps = getRegistryOps(registryAccess);
		List<BookmarkConfigEntry> entries = BookmarkJsonSerializer.createEntries(bookmarkList, bookmarks);
		try {
			JsonArrayFileHelper.write(
				path,
				VERSION,
				entries,
				entryCodec,
				registryOps,
				error -> LOGGER.error("Encountered an error when saving bookmark config to file {}\n{}", path, error),
				(entry, exception) -> LOGGER.error("Encountered an exception when saving bookmark config to file {}\n{}", path, entry, exception)
			);
			LOGGER.debug("Saved bookmarks config to file: {}", path);
			return true;
		} catch (RuntimeException | IOException e) {
			LOGGER.error("Failed to save bookmarks config to file {}", path, e);
			return false;
		}
	}

	@SuppressWarnings("deprecation")
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
		BookmarkFactory bookmarkFactory,
		RecipeTransferService recipeTransferService
	) {
		this.bookmarkList = bookmarkList;
		this.ingredientManager = ingredientManager;
		this.codecHelper = codecHelper;
		RegistryOps<JsonElement> registryOps = getRegistryOps(registryAccess);
		loadJsonBookmarks(ingredientManager, recipeManager, registryOps, codecHelper, bookmarkCodec);
		List<IBookmark> legacyBookmarks = new ArrayList<>(legacyBookmarkConfig.loadBookmarks(
			recipeManager, focusFactory, ingredientManager, registryAccess, bookmarkFactory, recipeTransferService));
		legacyBookmarks.addAll(loadLegacyCompressedJsonBookmarks(ingredientManager, recipeManager, registryAccess, codecHelper, bookmarkCodec));
		if (!legacyBookmarks.isEmpty()) {
			for (IBookmark bookmark : legacyBookmarks) {
				bookmarkList.addToListWithoutNotifying(bookmark, false);
			}
			getPath(jeiConfigurationDir).ifPresent(path -> migrate(path, registryOps, bookmarkCodec));
		}
		bookmarkList.notifyListenersOfChange();
	}

	@SuppressWarnings("deprecation")
	private void migrate(Path path, RegistryOps<JsonElement> registryOps, Codec<IBookmark> bookmarkCodec) {
		try {
			BookmarkJsonSerializer.migrate(
				path, VERSION, LegacyBookmarkConfig.getPath(jeiConfigurationDir),
				BookmarkJsonSerializer.createEntries(bookmarkList),
				BookmarkConfigEntryCodec.create(codecHelper, ingredientManager, bookmarkCodec), registryOps);
		} catch (RuntimeException | IOException e) {
			LOGGER.error("Failed to migrate bookmark config to file {}", path, e);
		}
	}

	@Unmodifiable
	private List<IBookmark> loadJsonBookmarks(
		IIngredientManager ingredientManager,
		IRecipeManager recipeManager,
		RegistryOps<JsonElement> registryOps,
		ICodecHelper codecHelper,
		Codec<IBookmark> bookmarkCodec
	) {
		return getPath(jeiConfigurationDir)
			.<List<IBookmark>>map(path -> {
				if (!Files.exists(path)) {
					return List.of();
				}

				List<IBookmark> bookmarks;
				Codec<BookmarkConfigEntry> entryCodec = BookmarkConfigEntryCodec.create(codecHelper, ingredientManager, bookmarkCodec);

				try (BufferedReader reader = Files.newBufferedReader(path)) {
					List<BookmarkConfigEntry> entries = JsonArrayFileHelper.read(
						reader,
						VERSION,
						entryCodec,
						registryOps,
						(element, error) -> {
							LOGGER.error("Encountered an error when loading the bookmark config from file {}\n{}\n{}", path, element, error);
						},
						(element, exception) -> {
							LOGGER.error("Encountered an exception when loading the bookmark config from file {}\n{}", path, element, exception);
						}
					);
					BookmarkJsonSerializer.applyEntriesWithoutNotifying(entries, bookmarkList);
					bookmarks = bookmarkList.getBookmarks();
					LOGGER.debug("Loaded bookmarks config from file: {}", path);
				} catch (RuntimeException | IOException e) {
					LOGGER.error("Failed to load bookmarks from file {}", path, e);
					bookmarks = new ArrayList<>();
				}

				return bookmarks;
			})
			.orElseGet(List::of);
	}

	@Unmodifiable
	private List<IBookmark> loadLegacyCompressedJsonBookmarks(
		IIngredientManager ingredientManager,
		IRecipeManager recipeManager,
		RegistryAccess registryAccess,
		ICodecHelper codecHelper,
		Codec<IBookmark> bookmarkCodec
	) {
		return getPath(jeiConfigurationDir)
			.<List<IBookmark>>map(path -> {
				if (!Files.exists(path)) {
					return List.of();
				}

				List<IBookmark> bookmarks;

				RegistryOps<JsonElement> compressedOps = registryAccess.createSerializationContext(JsonOps.COMPRESSED);

				try (BufferedReader reader = Files.newBufferedReader(path)) {
					bookmarks = JsonArrayFileHelper.read(
						reader,
						null,
						bookmarkCodec,
						compressedOps,
						(element, error) -> {
							// ignore errors
						},
						(element, exception) -> {
							// ignore errors
						}
					);
					LOGGER.debug("Loaded legacy compressed json bookmarks config from file: {}", path);
				} catch (RuntimeException | IOException e) {
					LOGGER.error("Failed to load legacy compressed json bookmarks from file {}", path, e);
					bookmarks = new ArrayList<>();
				}

				return bookmarks;
			})
			.orElseGet(List::of);
	}
}

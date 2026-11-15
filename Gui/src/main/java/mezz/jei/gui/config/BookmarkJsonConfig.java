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
import org.jetbrains.annotations.Nullable;

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
	private static final int VERSION = 3;

	@SuppressWarnings("deprecation")
	private final LegacyBookmarkConfig legacyBookmarkConfig;
	private final Path jeiConfigurationDir;
	private BookmarkList bookmarkList;
	private IIngredientManager ingredientManager;
	private ICodecHelper codecHelper;
	private final DeduplicatingRunner delayedSave = new DeduplicatingRunner(SAVE_DELAY_TIME, Internal.getDelayedExecutor());
	private @Nullable JsonConfigFileGuard fileGuard;

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
		List<BookmarkConfigEntry> entriesSnapshot = bookmarkList.createChapterEntries();
		JsonConfigFileGuard guard = fileGuard;
		if (guard == null) {
			return false;
		}
		return getPath(jeiConfigurationDir)
			.filter(guard::canWrite)
			.map(path -> {
				delayedSave.run(() -> saveEntries(path, registryAccess, entriesSnapshot, bookmarkCodec, guard));
				return true;
			})
			.orElse(false);
	}

	private boolean save(Path path, RegistryAccess registryAccess, Collection<IBookmark> bookmarks, Codec<IBookmark> bookmarkCodec) {
		JsonConfigFileGuard guard = fileGuard;
		return guard != null && saveEntries(path, registryAccess, bookmarkList.createChapterEntries(), bookmarkCodec, guard);
	}

	private boolean saveEntries(Path path, RegistryAccess registryAccess, List<BookmarkConfigEntry> entries, Codec<IBookmark> bookmarkCodec, JsonConfigFileGuard guard) {
		Codec<BookmarkConfigEntry> entryCodec = BookmarkConfigEntryCodec.create(codecHelper, ingredientManager, bookmarkCodec);
		RegistryOps<JsonElement> registryOps = getRegistryOps(registryAccess);
		try {
			return guard.write(
				path,
				VERSION,
				entries,
				entryCodec,
				registryOps
			);
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
		if (fileGuard != null) {
			fileGuard.preventWrites();
		}
		fileGuard = getPath(jeiConfigurationDir).map(path -> new JsonConfigFileGuard(path, true)).orElse(null);
		try {
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
		} catch (RuntimeException e) {
			if (fileGuard != null) {
				fileGuard.preventLossyWrite();
			}
			throw e;
		} finally {
			if (fileGuard != null) {
				fileGuard.finishLoading();
			}
		}
	}

	@SuppressWarnings("deprecation")
	private void migrate(Path path, RegistryOps<JsonElement> registryOps, Codec<IBookmark> bookmarkCodec) {
		if (fileGuard == null || !fileGuard.canWrite(path)) {
			return;
		}
		try {
			BookmarkJsonSerializer.migrate(
				path, VERSION, LegacyBookmarkConfig.getPath(jeiConfigurationDir),
				bookmarkList.createChapterEntries(),
				BookmarkConfigEntryCodec.create(codecHelper, ingredientManager, bookmarkCodec), registryOps, fileGuard);
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
				List<IBookmark> bookmarks;
				Codec<BookmarkConfigEntry> entryCodec = BookmarkConfigEntryCodec.create(codecHelper, ingredientManager, bookmarkCodec);

				try {
					List<BookmarkConfigEntry> entries = readEntries(path, VERSION, entryCodec, registryOps);
					if (entries.isEmpty()) {
						entries = readEntries(path, 2, entryCodec, registryOps);
						if (!entries.isEmpty()) {
							Path backup = path.resolveSibling("bookmarks.before-chapters.json");
							if (!Files.exists(backup))
								Files.copy(path, backup);
						}
					}
					BookmarkJsonSerializer.applyEntriesWithoutNotifying(entries, bookmarkList);
					List<BookmarkConfigEntry> loadedEntries = bookmarkList.createChapterEntries();
					boolean lostEntries = loadedEntries.stream().filter(entry -> entry.bookmark() != null).count() < entries.stream().filter(entry -> entry.bookmark() != null).count() ||
						loadedEntries.stream().filter(entry -> entry.chapter() != null).count() < entries.stream().filter(entry -> entry.chapter() != null).count() ||
						loadedEntries.stream().filter(entry -> entry.group() != null).count() < entries.stream().filter(entry -> entry.group() != null).count();
					if (lostEntries && fileGuard != null) {
						fileGuard.preventLossyWrite();
						LOGGER.warn("Bookmark records in {} have conflicting identities. The original JSON will not be overwritten.", path);
					}
					bookmarks = bookmarkList.getBookmarks();
					LOGGER.debug("Loaded bookmarks config from file: {}", path);
				} catch (RuntimeException | IOException e) {
					if (fileGuard != null) {
						fileGuard.preventLossyWrite();
					}
					LOGGER.error("Failed to load bookmarks from file {}", path, e);
					bookmarks = new ArrayList<>();
				}

				return bookmarks;
			})
			.orElseGet(List::of);
	}

	private List<BookmarkConfigEntry> readEntries(Path path, int version, Codec<BookmarkConfigEntry> codec, RegistryOps<JsonElement> ops) throws IOException {
		JsonConfigFileGuard guard = fileGuard;
		if (guard == null) {
			return List.of();
		}
		return guard.read(version, codec, ops,
			(element, error) -> LOGGER.error("Unable to read bookmark entry in {}: {}: {}", path, element, error),
			(element, exception) -> LOGGER.error("Unable to read bookmark entry in {}: {}", path, element, exception));
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
				if (!Files.exists(path) || fileGuard == null) {
					return List.of();
				}

				List<IBookmark> bookmarks;

				RegistryOps<JsonElement> compressedOps = registryAccess.createSerializationContext(JsonOps.COMPRESSED);

				try {
					bookmarks = fileGuard.read(
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
					fileGuard.preventLossyWrite();
					LOGGER.error("Failed to load legacy compressed json bookmarks from file {}", path, e);
					bookmarks = new ArrayList<>();
				}

				return bookmarks;
			})
			.orElseGet(List::of);
	}
}

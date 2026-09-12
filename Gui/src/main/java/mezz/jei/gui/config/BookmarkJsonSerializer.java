package mezz.jei.gui.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mezz.jei.common.chat.JeiChatItemLinks;
import mezz.jei.common.config.file.JsonArrayFileHelper;
import mezz.jei.common.util.PathUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.RecipeBookmark;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class BookmarkJsonSerializer {
	private static final Logger LOGGER = LogManager.getLogger();
	private BookmarkJsonSerializer() {
	}

	public static void migrate(Path path, int version, Optional<Path> legacyPath, List<BookmarkConfigEntry> entries,
		Codec<BookmarkConfigEntry> codec, DynamicOps<JsonElement> ops) throws IOException {
		if (Files.exists(path)) {
			Path backup = path.resolveSibling(path.getFileName() + ".bak");
			Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.info("Backed up legacy json compressed bookmarks config file to '{}'", backup);
		}
		// Abort the atomic write on any encoding error before retiring the legacy source.
		JsonArrayFileHelper.write(path, version, entries, codec, ops,
			error -> { throw new IllegalStateException(error.message()); },
			(entry, exception) -> { throw exception; });
		if (legacyPath.isPresent() && Files.exists(legacyPath.get())) {
			Path backup = legacyPath.get().resolveSibling(legacyPath.get().getFileName() + ".bak");
			PathUtil.moveAtomicReplace(legacyPath.get(), backup);
			LOGGER.info("Backed up legacy bookmarks config file to '{}'", backup);
		}
	}

	public static List<BookmarkConfigEntry> createEntries(BookmarkList bookmarkList) {
		return createEntries(bookmarkList, bookmarkList.getBookmarks());
	}

	static List<BookmarkConfigEntry> createEntries(BookmarkList bookmarkList, Collection<IBookmark> bookmarks) {
		List<BookmarkConfigEntry> entries = new ArrayList<>();
		bookmarkList.getBookmarkGroups().stream()
			.filter(group -> group.id() != BookmarkGroupManager.DEFAULT_GROUP_ID)
			.map(BookmarkConfigEntry::group)
			.forEach(entries::add);
		for (IBookmark bookmark : bookmarks) {
			entries.add(BookmarkConfigEntry.bookmark(bookmark, bookmarkList.getBookmarkMetadata(bookmark)));
		}
		return List.copyOf(entries);
	}

	public static void applyEntries(List<BookmarkConfigEntry> entries, BookmarkList bookmarkList) {
		applyEntriesWithoutNotifying(entries, bookmarkList);
		bookmarkList.notifyListenersOfChange();
	}

	static void applyEntriesWithoutNotifying(List<BookmarkConfigEntry> entries, BookmarkList bookmarkList) {
		for (BookmarkConfigEntry entry : entries) {
			if (entry.group() != null) {
				bookmarkList.addGroupFromConfig(entry.group());
			} else if (entry.bookmark() != null && entry.metadata() != null) {
				bookmarkList.addToListWithoutNotifying(entry.bookmark(), false);
				bookmarkList.moveBookmarkMetadataFromConfig(entry.bookmark(), entry.metadata());
			}
		}
	}

	public static Optional<String> serializeGroupSnapshot(
		BookmarkList bookmarkList,
		int groupId,
		Codec<BookmarkConfigEntry> entryCodec,
		DynamicOps<JsonElement> registryOps
	) {
		Optional<BookmarkGroup> optionalGroup = bookmarkList.getBookmarkGroups().stream()
			.filter(group -> group.id() == groupId)
			.findFirst();
		if (optionalGroup.isEmpty()) {
			return Optional.empty();
		}
		List<BookmarkConfigEntry> bookmarks = bookmarkList.getBookmarks().stream()
			.filter(bookmark -> groupId == bookmarkList.getBookmarkGroupId(bookmark))
			.map(bookmark -> BookmarkConfigEntry.bookmark(bookmark, bookmarkList.getBookmarkMetadata(bookmark)))
			.toList();
		if (bookmarks.isEmpty() || bookmarks.size() > JeiChatItemLinks.MAX_BOOKMARK_GROUP_ENTRIES) {
			return Optional.empty();
		}

		BookmarkGroup sourceGroup = optionalGroup.get();
		String title = sourceGroup.title().substring(0, Math.min(sourceGroup.title().length(), JeiChatItemLinks.MAX_BOOKMARK_GROUP_TITLE_LENGTH));
		BookmarkGroup group = new BookmarkGroup(
			sourceGroup.id(),
			title,
			sourceGroup.viewMode(),
			sourceGroup.collapsed(),
			sourceGroup.craftingMode(),
			sourceGroup.collapsedRecipeIds()
		);
		GroupSnapshot snapshot = new GroupSnapshot(JeiChatItemLinks.BOOKMARK_GROUP_SNAPSHOT_VERSION, group, bookmarks);
		return createGroupSnapshotCodec(entryCodec)
			.encodeStart(registryOps, snapshot)
			.result()
			.map(JsonElement::toString)
			.map(json -> Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8)))
			.filter(JeiChatItemLinks::isBookmarkGroupLinkLengthValid);
	}

	public static Optional<Integer> deserializeGroupSnapshot(
		String encoded,
		BookmarkList bookmarkList,
		Codec<BookmarkConfigEntry> entryCodec,
		DynamicOps<JsonElement> registryOps
	) {
		// Shared snapshots are remote player input, so malformed Base64, JSON, and entries are rejected here.
		try {
			if (!JeiChatItemLinks.isBookmarkGroupLinkLengthValid(encoded)) {
				return Optional.empty();
			}
			String jsonText = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			Optional<GroupSnapshot> decoded = createGroupSnapshotCodec(entryCodec)
				.parse(registryOps, JsonParser.parseString(jsonText))
				.result();
			if (decoded.isEmpty()) {
				return Optional.empty();
			}
			GroupSnapshot snapshot = decoded.get();
			if (snapshot.version() != JeiChatItemLinks.BOOKMARK_GROUP_SNAPSHOT_VERSION ||
				snapshot.group().title().length() > JeiChatItemLinks.MAX_BOOKMARK_GROUP_TITLE_LENGTH ||
				snapshot.bookmarks().isEmpty() ||
				snapshot.bookmarks().size() > JeiChatItemLinks.MAX_BOOKMARK_GROUP_ENTRIES ||
				snapshot.bookmarks().stream().anyMatch(entry -> entry.bookmark() == null || entry.metadata() == null)
			) {
				return Optional.empty();
			}

			BookmarkGroup sharedGroup = snapshot.group();
			int groupId = bookmarkList.createGroup(sharedGroup.title());
			bookmarkList.addGroupFromConfig(new BookmarkGroup(
				groupId,
				sharedGroup.title(),
				sharedGroup.viewMode(),
				sharedGroup.collapsed(),
				sharedGroup.craftingMode(),
				sharedGroup.collapsedRecipeIds()
			));
			boolean imported = false;
			for (BookmarkConfigEntry entry : snapshot.bookmarks()) {
				IBookmark bookmark = withGroupId(entry.bookmark(), groupId);
				if (bookmarkList.addToListWithoutNotifying(bookmark, false)) {
					bookmarkList.moveBookmarkMetadataFromConfig(bookmark, entry.metadata().withGroupId(groupId));
					imported = true;
				}
			}
			if (!imported) {
				bookmarkList.removeGroup(groupId);
				return Optional.empty();
			}
			bookmarkList.notifyListenersOfChange();
			bookmarkList.setGroupViewMode(groupId, sharedGroup.viewMode());
			return Optional.of(groupId);
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static IBookmark withGroupId(IBookmark bookmark, int groupId) {
		if (bookmark instanceof IngredientBookmark<?> ingredientBookmark) {
			return ingredientBookmark.withEqualityScope(groupId);
		}
		return ((RecipeBookmark) bookmark).withEqualityScope(groupId);
	}

	private static Codec<GroupSnapshot> createGroupSnapshotCodec(Codec<BookmarkConfigEntry> entryCodec) {
		return RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("version").forGetter(GroupSnapshot::version),
				BookmarkConfigEntryCodec.GROUP_CODEC.fieldOf("group").forGetter(GroupSnapshot::group),
				entryCodec.listOf().fieldOf("bookmarks").forGetter(GroupSnapshot::bookmarks)
			)
			.apply(instance, GroupSnapshot::new));
	}

	private record GroupSnapshot(int version, BookmarkGroup group, List<BookmarkConfigEntry> bookmarks) {
	}
}

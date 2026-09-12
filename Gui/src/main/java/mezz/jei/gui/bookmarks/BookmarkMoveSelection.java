package mezz.jei.gui.bookmarks;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record BookmarkMoveSelection(
	List<IBookmark> bookmarks,
	boolean movesRecipe,
	boolean crossGroupMove,
	Set<ResourceLocation> recipeIds
) {
	public BookmarkMoveSelection {
		bookmarks = List.copyOf(bookmarks);
		recipeIds = Set.copyOf(recipeIds);
	}

	public BookmarkMoveSelection(List<IBookmark> bookmarks, boolean movesRecipe) {
		this(bookmarks, movesRecipe, false, Set.of());
	}

	public static BookmarkMoveSelection create(BookmarkList bookmarkList, IBookmark draggedBookmark) {
		BookmarkItemMetadata metadata = bookmarkList.getBookmarkMetadata(draggedBookmark);
		Optional<ResourceLocation> recipeUid = Optional.ofNullable(metadata.recipeUid());
		if (!metadata.type().isGraphOutput() ||
			recipeUid.isEmpty() ||
			!canMoveRecipe(bookmarkList, metadata.groupId())
		) {
			return new BookmarkMoveSelection(List.of(draggedBookmark), false);
		}

		Set<ResourceLocation> recipeIds = getMoveRecipeIds(bookmarkList, metadata);
		List<IBookmark> recipeBookmarks = bookmarkList.getBookmarks().stream()
			.filter(bookmark -> {
				BookmarkItemMetadata bookmarkMetadata = bookmarkList.getBookmarkMetadata(bookmark);
				return metadata.groupId() == bookmarkMetadata.groupId() &&
					bookmarkMetadata.type().isRecipeAssociated() &&
					recipeIds.contains(bookmarkMetadata.recipeUid());
			})
			.toList();
		if (recipeBookmarks.isEmpty()) {
			return new BookmarkMoveSelection(List.of(draggedBookmark), false);
		}
		return new BookmarkMoveSelection(recipeBookmarks, true, canCrossGroupMove(bookmarkList, metadata), recipeIds);
	}

	public void moveToBookmark(BookmarkList bookmarkList, IBookmark targetBookmark, int targetGroupId, int offset) {
		MoveTarget target = getEffectiveTarget(bookmarkList, targetBookmark, targetGroupId, offset);
		bookmarkList.moveBookmarks(bookmarks, target.bookmark(), targetGroupId, target.offset());
	}

	private static boolean canMoveRecipe(BookmarkList bookmarkList, int groupId) {
		return bookmarkList.getBookmarkGroups().stream()
			.filter(group -> group.id() == groupId)
			.findFirst()
			.map(group -> group.craftingMode() || !group.collapsed() && group.viewMode() == BookmarkViewMode.TODO_LIST)
			.orElse(false);
	}

	private static boolean canCrossGroupMove(BookmarkList bookmarkList, BookmarkItemMetadata metadata) {
		return bookmarkList.getBookmarkGroups().stream()
			.filter(group -> group.id() == metadata.groupId())
			.findFirst()
			.map(group -> !group.collapsed() && group.viewMode() == BookmarkViewMode.TODO_LIST)
			.orElse(false);
	}

	private static Set<ResourceLocation> getMoveRecipeIds(BookmarkList bookmarkList, BookmarkItemMetadata metadata) {
		ResourceLocation recipeUid = metadata.recipeUid();
		if (recipeUid == null) {
			return Set.of();
		}
		Set<ResourceLocation> recipeIds = new LinkedHashSet<>();
		recipeIds.add(recipeUid);

		bookmarkList.getBookmarkGroups().stream()
			.filter(group -> group.id() == metadata.groupId())
			.findFirst()
			.filter(group -> group.collapsedRecipeIds().contains(recipeUid))
			.flatMap(group -> bookmarkList.getRecipeChainDetails(group.id()))
			.map(details -> details.recipeRelations().get(recipeUid))
			.ifPresent(recipeIds::addAll);

		return recipeIds;
	}

	private MoveTarget getEffectiveTarget(
		BookmarkList bookmarkList,
		IBookmark targetBookmark,
		int targetGroupId,
		int offset
	) {
		if (!movesRecipe) {
			return new MoveTarget(targetBookmark, offset);
		}

		BookmarkItemMetadata targetMetadata = bookmarkList.getBookmarkMetadata(targetBookmark);
		ResourceLocation targetRecipeUid = targetMetadata.recipeUid();
		if (targetRecipeUid == null ||
			!targetMetadata.type().isRecipeAssociated() ||
			recipeIds.contains(targetRecipeUid) ||
			targetGroupId != targetMetadata.groupId()
		) {
			return new MoveTarget(targetBookmark, offset);
		}

		List<IBookmark> orderedBookmarks = bookmarkList.getBookmarks();
		for (IBookmark bookmark : offset <= 0 ? orderedBookmarks : orderedBookmarks.reversed()) {
			BookmarkItemMetadata metadata = bookmarkList.getBookmarkMetadata(bookmark);
			if (targetGroupId == metadata.groupId() &&
				metadata.type().isRecipeAssociated() &&
				targetRecipeUid.equals(metadata.recipeUid())
			) {
				return new MoveTarget(bookmark, offset <= 0 ? 0 : 1);
			}
		}
		return new MoveTarget(targetBookmark, offset);
	}

	private record MoveTarget(IBookmark bookmark, int offset) {
	}
}

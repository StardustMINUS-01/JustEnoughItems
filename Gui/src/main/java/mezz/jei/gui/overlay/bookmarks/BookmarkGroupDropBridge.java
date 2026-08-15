package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlayLayout.GroupPanelSlot;
import net.minecraft.client.renderer.Rect2i;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Bridge for dropping bookmark groups onto third-party targets (e.g. AE2 terminals).
 */
public final class BookmarkGroupDropBridge {
	@FunctionalInterface
	public interface GroupDropHandler {
		boolean dropGroup(List<ITypedIngredient<?>> ingredients, double mouseX, double mouseY);
	}

	@FunctionalInterface
	public interface GroupDropAvailabilityProvider {
		boolean hasDropTargets(List<ITypedIngredient<?>> ingredients);
	}

	@FunctionalInterface
	public interface GroupDropHighlightProvider {
		List<Rect2i> getDropAreas(List<ITypedIngredient<?>> ingredients);
	}

	private static @Nullable GroupDropHandler groupDropHandler;
	private static @Nullable GroupDropAvailabilityProvider groupDropAvailabilityProvider;
	private static @Nullable GroupDropHighlightProvider groupDropHighlightProvider;

	private BookmarkGroupDropBridge() {
	}

	static void setGroupDropHandler(@Nullable GroupDropHandler groupDropHandler) {
		BookmarkGroupDropBridge.groupDropHandler = groupDropHandler;
	}

	static void setGroupDropAvailabilityProvider(@Nullable GroupDropAvailabilityProvider groupDropAvailabilityProvider) {
		BookmarkGroupDropBridge.groupDropAvailabilityProvider = groupDropAvailabilityProvider;
	}

	static void setGroupDropHighlightProvider(@Nullable GroupDropHighlightProvider groupDropHighlightProvider) {
		BookmarkGroupDropBridge.groupDropHighlightProvider = groupDropHighlightProvider;
	}

	static @Nullable GroupDropHandler getGroupDropHandler() {
		return groupDropHandler;
	}

	static @Nullable GroupDropHighlightProvider getGroupDropHighlightProvider() {
		return groupDropHighlightProvider;
	}

	static boolean canStartGroupDrop(BookmarkList bookmarkList, String groupId) {
		if (groupDropHandler == null || groupDropAvailabilityProvider == null) {
			return false;
		}
		List<ITypedIngredient<?>> ingredients = getGroupDropIngredients(bookmarkList, groupId);
		return !ingredients.isEmpty() && groupDropAvailabilityProvider.hasDropTargets(ingredients);
	}

	static List<ITypedIngredient<?>> getGroupDropIngredients(BookmarkList bookmarkList, String groupId) {
		IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
		Set<BookmarkIngredientKey> seen = new HashSet<>();
		List<ITypedIngredient<?>> ingredients = new ArrayList<>();
		for (IBookmark bookmark : bookmarkList.getBookmarks()) {
			if (!isGroupDropBookmark(bookmarkList, groupId, bookmark)) {
				continue;
			}
			ITypedIngredient<?> ingredient = bookmark.getElement().getTypedIngredient();
			BookmarkIngredientKey key = BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager);
			if (seen.add(key)) {
				ingredients.add(ingredient);
			}
		}
		return List.copyOf(ingredients);
	}

	static List<BookmarkPanelLayout.PanelSlot<IBookmark>> getGroupDropPanelSlots(
		BookmarkList bookmarkList,
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> groupSlots,
		String groupId
	) {
		IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
		Set<BookmarkIngredientKey> seen = new HashSet<>();
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> result = new ArrayList<>();
		for (BookmarkPanelLayout.PanelSlot<IBookmark> slot : groupSlots) {
			if (!isGroupDropBookmark(bookmarkList, groupId, slot.item())) {
				continue;
			}
			BookmarkIngredientKey key = BookmarkItemMetadataFactory.createPermutationKey(slot.item().getElement().getTypedIngredient(), ingredientManager);
			if (seen.add(key)) {
				result.add(slot);
			}
		}
		return result;
	}

	static Optional<ITypedIngredient<?>> getGroupDropHoverIngredient(BookmarkList bookmarkList, Optional<GroupPanelSlot> hoveredSlot) {
		if (groupDropHighlightProvider == null) {
			return Optional.empty();
		}
		return hoveredSlot.flatMap(slot -> getGroupDropIngredients(bookmarkList, slot.groupId()).stream().findFirst());
	}

	private static boolean isGroupDropBookmark(BookmarkList bookmarkList, String groupId, IBookmark bookmark) {
		if (!groupId.equals(bookmarkList.getBookmarkGroupId(bookmark))) {
			return false;
		}
		BookmarkItemMetadata metadata = bookmarkList.getBookmarkMetadata(bookmark);
		return metadata.type() == BookmarkItemType.RESULT || metadata.type() == BookmarkItemType.ITEM;
	}
}

package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
import mezz.jei.gui.bookmarks.BookmarkDisplaySlot;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.input.IPaged;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Owns the bookmark panel slot layout and its cached panel snapshot.
 */
public class BookmarkOverlayLayout {
	private final BookmarkList bookmarkList;
	private final IngredientGridWithNavigation contents;
	private @Nullable PanelSnapshotKey panelSnapshotKey;
	private @Nullable PanelSnapshot panelSnapshot;

	public BookmarkOverlayLayout(BookmarkList bookmarkList, IngredientGridWithNavigation contents) {
		this.bookmarkList = bookmarkList;
		this.contents = contents;
	}

	List<BookmarkPanelLayout.PanelSlot<IBookmark>> getPanelSlots() {
		return getPanelSnapshot().panelSlots();
	}

	List<BookmarkPanelLayout.PanelSlot<IBookmark>> getProjectedPanelSlots() {
		return getPanelSnapshot().projectedPanelSlots();
	}

	List<BookmarkPanelLayout.RowSlot<IBookmark>> getGroupPanelRowSlots() {
		return getPanelSnapshot().rowSlots();
	}

	List<GroupPanelSlot> getGroupPanelSlots() {
		return getPanelSnapshot().groupPanelSlots();
	}

	private PanelSnapshot getPanelSnapshot() {
		List<IngredientListSlot> visibleSlots = this.contents.getSlots().toList();
		List<ImmutableRect2i> pageAreas = visibleSlots.stream()
			.map(IngredientListSlot::getArea)
			.toList();
		List<VisibleSlotKey> visibleContentKeys = visibleSlots.stream()
			.map(BookmarkOverlayLayout::createVisibleSlotKey)
			.toList();
		IPaged pageDelegate = this.contents.getPageDelegate();
		PanelSnapshotKey key = new PanelSnapshotKey(
			bookmarkList.getChangeVersion(),
			this.contents.getUsableColumnCount(),
			pageDelegate.getPageNumber(),
			this.contents.size(),
			pageAreas,
			visibleContentKeys
		);
		if (!key.equals(panelSnapshotKey) || panelSnapshot == null) {
			panelSnapshotKey = key;
			panelSnapshot = createPanelSnapshot(visibleSlots, pageAreas, key);
		}
		return panelSnapshot;
	}

	private PanelSnapshot createPanelSnapshot(
		List<IngredientListSlot> visibleSlots,
		List<ImmutableRect2i> pageAreas,
		PanelSnapshotKey key
	) {
		List<BookmarkDisplaySlot<IBookmark>> displaySlots = this.bookmarkList.getDisplaySlots(
			this.contents.getUsableColumnCount(),
			this.contents.getUsableColumnsPerRow()
		);
		int firstDisplaySlotIndex = key.pageNumber() * key.pageSize();
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> projectedPanelSlots = BookmarkPanelLayout.createPagePanelSlots(
			displaySlots,
			pageAreas,
			firstDisplaySlotIndex
		);
		int gridLeftX = pageAreas.stream()
			.mapToInt(ImmutableRect2i::getX)
			.min()
			.orElseGet(() -> projectedPanelSlots.stream()
				.map(BookmarkPanelLayout.PanelSlot::area)
				.mapToInt(ImmutableRect2i::getX)
				.min()
				.orElse(0));
		List<GroupPanelSlot> groupPanelSlots = BookmarkPanelLayout.createRowSlots(projectedPanelSlots, gridLeftX).stream()
			.map(slot -> new GroupPanelSlot(slot.item(), slot.groupId(), slot.area()))
			.toList();
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots = createPanelSlots(visibleSlots, displaySlots, firstDisplaySlotIndex);
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots = toRowSlots(groupPanelSlots);
		return new PanelSnapshot(panelSlots, projectedPanelSlots, groupPanelSlots, rowSlots);
	}

	void clearPanelSnapshot() {
		panelSnapshotKey = null;
		panelSnapshot = null;
	}

	private List<BookmarkPanelLayout.PanelSlot<IBookmark>> createPanelSlots(
		List<IngredientListSlot> visibleSlots,
		List<BookmarkDisplaySlot<IBookmark>> displaySlots,
		int firstDisplaySlotIndex
	) {
		Map<Integer, BookmarkDisplaySlot<IBookmark>> displaySlotByIndex = new HashMap<>();
		Map<IBookmark, BookmarkDisplaySlot<IBookmark>> displaySlotByBookmark = new HashMap<>();
		for (BookmarkDisplaySlot<IBookmark> displaySlot : displaySlots) {
			displaySlotByIndex.put(displaySlot.slotIndex(), displaySlot);
			displaySlotByBookmark.putIfAbsent(displaySlot.entry().item(), displaySlot);
		}
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots = new ArrayList<>();
		for (int i = 0; i < visibleSlots.size(); i++) {
			IngredientListSlot visibleSlot = visibleSlots.get(i);
			Optional<IBookmark> bookmark = visibleSlot.getOptionalElement()
				.flatMap(IElement::getBookmark);
			if (bookmark.isEmpty()) {
				continue;
			}
			Optional<BookmarkDisplaySlot<IBookmark>> displaySlot = getDisplaySlot(
				displaySlotByIndex,
				displaySlotByBookmark,
				firstDisplaySlotIndex + i,
				bookmark.get()
			);
			String groupId = displaySlot
				.map(slot -> slot.entry().metadata().groupId())
				.orElseGet(() -> this.bookmarkList.getBookmarkGroupId(bookmark.get()));
			boolean shadow = displaySlot
				.map(BookmarkDisplaySlot::shadow)
				.orElse(false);
			Object recipeKey = displaySlot
				.map(slot -> getRecipeKey(slot.entry()))
				.orElseGet(() -> getRecipeKey(this.bookmarkList.getBookmarkMetadata(bookmark.get())));
			panelSlots.add(new BookmarkPanelLayout.PanelSlot<>(bookmark.get(), groupId, visibleSlot.getArea(), shadow, recipeKey));
		}
		return panelSlots;
	}

	private static @Nullable ResourceLocation getRecipeKey(BookmarkDisplayEntry<IBookmark> entry) {
		if (!entry.metadata().type().isRecipeAssociated()) {
			return null;
		}
		return entry.displayRecipeUid()
			.orElse(entry.metadata().recipeUid());
	}

	private static @Nullable ResourceLocation getRecipeKey(BookmarkItemMetadata metadata) {
		if (!metadata.type().isRecipeAssociated()) {
			return null;
		}
		return metadata.recipeUid();
	}

	private static Optional<BookmarkDisplaySlot<IBookmark>> getDisplaySlot(
		Map<Integer, BookmarkDisplaySlot<IBookmark>> displaySlotByIndex,
		Map<IBookmark, BookmarkDisplaySlot<IBookmark>> displaySlotByBookmark,
		int displaySlotIndex,
		IBookmark bookmark
	) {
		return Optional.ofNullable(displaySlotByIndex.get(displaySlotIndex))
			.filter(displaySlot -> displaySlot.entry().item().equals(bookmark))
			.or(() -> Optional.ofNullable(displaySlotByBookmark.get(bookmark)));
	}

	private static VisibleSlotKey createVisibleSlotKey(IngredientListSlot slot) {
		int bookmarkIdentity = slot.getOptionalElement()
			.flatMap(IElement::getBookmark)
			.map(System::identityHashCode)
			.orElse(0);
		return new VisibleSlotKey(slot.getArea(), bookmarkIdentity);
	}

	static List<BookmarkPanelLayout.RowSlot<IBookmark>> toRowSlots(List<GroupPanelSlot> slots) {
		return slots.stream()
			.map(BookmarkOverlayLayout::toRowSlot)
			.toList();
	}

	static BookmarkPanelLayout.RowSlot<IBookmark> toRowSlot(GroupPanelSlot slot) {
		return new BookmarkPanelLayout.RowSlot<>(slot.bookmark(), slot.groupId(), slot.area());
	}

	record GroupPanelSlot(IBookmark bookmark, String groupId, ImmutableRect2i area) {
	}

	record VisibleSlotKey(ImmutableRect2i area, int bookmarkIdentity) {
	}

	record PanelSnapshotKey(
		long sourceVersion,
		int columns,
		int pageNumber,
		int pageSize,
		List<ImmutableRect2i> slotAreas,
		List<VisibleSlotKey> visibleContentKeys
	) {
	}

	record PanelSnapshot(
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> projectedPanelSlots,
		List<GroupPanelSlot> groupPanelSlots,
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots
	) {
	}
}

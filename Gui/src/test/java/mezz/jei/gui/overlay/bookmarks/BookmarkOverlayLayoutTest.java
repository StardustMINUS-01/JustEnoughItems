package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
import mezz.jei.gui.bookmarks.BookmarkDisplaySlot;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BookmarkOverlayLayoutTest {
	@Test
	public void groupHoverSelectsFirstOutputOrPlainIngredientInTheHoveredGroup() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BookmarkList bookmarks = new BookmarkList(null, null, null, null, null, null, null);
		var ingredientManager = ItemStackIngredientTestFixtures.ingredientManager();
		var entries = List.of(Items.IRON_INGOT, Items.COAL, Items.GOLD_INGOT, Items.DIAMOND).stream()
			.map(item -> IngredientBookmark.create(ItemStackIngredientTestFixtures.item(item), ingredientManager))
			.toList();
		entries.forEach(entry -> bookmarks.addToListWithoutNotifying(entry, false));
		bookmarks.addGroupFromConfig(new BookmarkGroup(1, "Group"));
		bookmarks.moveBookmarkMetadataFromConfig(entries.get(1), BookmarkItemMetadata.defaultForGroup(1).withType(BookmarkItemType.INGREDIENT));
		bookmarks.moveBookmarkMetadataFromConfig(entries.get(2), BookmarkItemMetadata.defaultForGroup(1).withType(BookmarkItemType.RESULT));
		bookmarks.moveBookmarkMetadataFromConfig(entries.get(3), BookmarkItemMetadata.defaultForGroup(1));
		var slot = new BookmarkOverlayLayout.GroupPanelSlot(entries.get(1), 1, new ImmutableRect2i(0, 0, 18, 18));
		var previousProvider = BookmarkGroupDropBridge.getGroupDropHighlightProvider();
		try {
			BookmarkGroupDropBridge.setGroupDropHighlightProvider(ingredients -> List.of());
			assertSame(entries.get(2).getElement().getTypedIngredient(), BookmarkGroupDropBridge.getGroupDropHoverIngredient(bookmarks, Optional.of(slot)).orElseThrow());
			assertTrue(BookmarkGroupDropBridge.getGroupDropHoverIngredient(bookmarks, Optional.empty()).isEmpty());
			BookmarkGroupDropBridge.setGroupDropHighlightProvider(null);
			assertTrue(BookmarkGroupDropBridge.getGroupDropHoverIngredient(bookmarks, Optional.of(slot)).isEmpty());
		} finally {
			BookmarkGroupDropBridge.setGroupDropHighlightProvider(previousProvider);
		}
	}

	@Test
	public void projectedPanelStartsAtRenderedFirstItemIndex() {
		List<BookmarkDisplaySlot<String>> displaySlots = List.of(
			createDisplaySlot(54, "before"),
			createDisplaySlot(55, "first"),
			createDisplaySlot(56, "second")
		);
		List<ImmutableRect2i> pageAreas = IntStream.range(0, 45)
			.mapToObj(index -> new ImmutableRect2i(index * 18, 0, 18, 18))
			.toList();
		BookmarkOverlayLayout.PanelSnapshotKey key = new BookmarkOverlayLayout.PanelSnapshotKey(
			0,
			9,
			55,
			pageAreas,
			List.of()
		);

		List<BookmarkPanelLayout.PanelSlot<String>> projected = BookmarkOverlayLayout.createProjectedPanelSlots(
			displaySlots,
			pageAreas,
			key
		);

		assertEquals(List.of("first", "second"), projected.stream().map(BookmarkPanelLayout.PanelSlot::item).toList());
		assertEquals(pageAreas.subList(0, 2), projected.stream().map(BookmarkPanelLayout.PanelSlot::area).toList());
	}

	@Test
	public void panelSnapshotKeyChangesWhenSmoothScrollMovesSlotAreas() {
		BookmarkOverlayLayout.PanelSnapshotKey first = new BookmarkOverlayLayout.PanelSnapshotKey(
			0,
			9,
			55,
			List.of(new ImmutableRect2i(0, 0, 18, 18)),
			List.of()
		);
		BookmarkOverlayLayout.PanelSnapshotKey partiallyScrolled = new BookmarkOverlayLayout.PanelSnapshotKey(
			0,
			9,
			55,
			List.of(new ImmutableRect2i(0, -6, 18, 18)),
			List.of()
		);

		assertNotEquals(first, partiallyScrolled);
	}

	@Test
	public void visibleRowConnectsToAdjacentOffscreenRows() {
		List<BookmarkDisplaySlot<String>> displaySlots = List.of(
			createDisplaySlot(0, "previous-1", 1),
			createDisplaySlot(1, "previous-2", 1),
			createDisplaySlot(2, "visible-1", 1),
			createDisplaySlot(3, "visible-2", 1),
			createDisplaySlot(4, "next-1", 1),
			createDisplaySlot(5, "next-2", 1)
		);
		ImmutableRect2i visibleArea = new ImmutableRect2i(0, 0, 18, 18);

		BookmarkOverlayLayout.BoundaryConnections result = BookmarkOverlayLayout.calculateBoundaryConnections(
			displaySlots,
			List.of(visibleArea, visibleArea.moveRight(18)),
			2,
			2,
			List.of(2),
			List.of(new BookmarkPanelLayout.RowSlot<>("visible-1", 1, visibleArea))
		);

		assertTrue(result.connectedToPrevious());
		assertTrue(result.connectedToNext());
	}

	@Test
	public void visibleRowDoesNotConnectAcrossGroupBoundaries() {
		List<BookmarkDisplaySlot<String>> displaySlots = List.of(
			createDisplaySlot(0, "previous", 2),
			createDisplaySlot(2, "visible", 1),
			createDisplaySlot(4, "next", 3)
		);
		ImmutableRect2i visibleArea = new ImmutableRect2i(0, 0, 18, 18);

		BookmarkOverlayLayout.BoundaryConnections result = BookmarkOverlayLayout.calculateBoundaryConnections(
			displaySlots,
			List.of(visibleArea, visibleArea.moveRight(18)),
			2,
			2,
			List.of(2),
			List.of(new BookmarkPanelLayout.RowSlot<>("visible", 1, visibleArea))
		);

		assertFalse(result.connectedToPrevious());
		assertFalse(result.connectedToNext());
	}

	@Test
	public void boundaryConnectionsUseVariableRowWidths() {
		List<BookmarkDisplaySlot<String>> displaySlots = List.of(
			createDisplaySlot(0, "previous-1", 1),
			createDisplaySlot(1, "previous-2", 1),
			createDisplaySlot(2, "visible", 1),
			createDisplaySlot(3, "next-1", 1),
			createDisplaySlot(4, "next-2", 1),
			createDisplaySlot(5, "next-3", 1)
		);
		ImmutableRect2i visibleArea = new ImmutableRect2i(0, 0, 18, 18);

		BookmarkOverlayLayout.BoundaryConnections result = BookmarkOverlayLayout.calculateBoundaryConnections(
			displaySlots,
			List.of(visibleArea),
			2,
			3,
			List.of(2, 1, 3),
			List.of(new BookmarkPanelLayout.RowSlot<>("visible", 1, visibleArea))
		);

		assertTrue(result.connectedToPrevious());
		assertTrue(result.connectedToNext());
	}

	private static BookmarkDisplaySlot<String> createDisplaySlot(int slotIndex, String item) {
		return createDisplaySlot(slotIndex, item, 1);
	}

	private static BookmarkDisplaySlot<String> createDisplaySlot(int slotIndex, String item, int groupId) {
		BookmarkDisplayEntry<String> entry = new BookmarkDisplayEntry<>(
			item,
			slotIndex,
			BookmarkItemMetadata.defaultForGroup(groupId),
			BookmarkViewMode.DEFAULT,
			false,
			Optional.empty(),
			Optional.empty(),
			false,
			false
		);
		return new BookmarkDisplaySlot<>(slotIndex, entry, false, false);
	}
}

package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkRowLayout;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.ghost.GhostIngredientDrag;
import mezz.jei.gui.input.MouseUtil;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlayLayout.GroupPanelSlot;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class GroupPanelDrag {
	private static final int GROUPING_PREVIEW_GROUP_ID = -1;
	private static final int GROUP_PANEL_DRAG_THRESHOLD_MS = 250;

	private final BookmarkOverlay overlay;
	private final GroupPanelSlot startSlot;
	private final boolean exclude;
	private final @Nullable BookmarkHotkeyAction clickFallbackAction;
	private final boolean dropMode;
	private final long startedAtMillis;
	private final double startX;
	private final double startY;
	private final int dragOffsetX;
	private final int dragOffsetY;
	private @Nullable GroupPanelSlot endSlot;
	// Ported from 1.21.1 fork (commit 29334012f): the cross-page state needed to
	// resolve any visible-slot Y into a global row that spans page boundaries.
	private final BookmarkRowLayout.RowLayout rows;
	private final List<BookmarkPanelLayout.PanelSlot<IBookmark>> slots;
	private final BookmarkPanelLayout.RowSlot<IBookmark> start;
	private @Nullable BookmarkGroupingPlan plan;
	private @Nullable BookmarkPanelLayout.RowSlot<IBookmark> globalEnd;
	private Set<IBookmark> selected = Set.of();
	private Set<IBookmark> released = Set.of();

	public GroupPanelDrag(
		BookmarkOverlay overlay,
		GroupPanelSlot startSlot,
		boolean exclude,
		@Nullable BookmarkHotkeyAction clickFallbackAction
	) {
		this(overlay, startSlot, exclude, clickFallbackAction, false);
	}

	public GroupPanelDrag(
		BookmarkOverlay overlay,
		GroupPanelSlot startSlot,
		boolean exclude,
		@Nullable BookmarkHotkeyAction clickFallbackAction,
		boolean dropMode
	) {
		this.overlay = overlay;
		this.startSlot = startSlot;
		this.exclude = exclude;
		this.clickFallbackAction = clickFallbackAction;
		this.dropMode = dropMode;
		this.startedAtMillis = System.currentTimeMillis();
		this.startX = MouseUtil.getX();
		this.startY = MouseUtil.getY();
		this.dragOffsetX = startSlot.area().getX() - (int) Math.round(this.startX);
		this.dragOffsetY = startSlot.area().getY() - (int) Math.round(this.startY);
		IngredientGridWithNavigation contents = overlay.getContents();
		this.rows = BookmarkRowLayout.RowLayout.create(contents.getUsableColumnCount(), contents.getUsableColumnsPerRow());
		this.slots = dropMode ? List.of() : BookmarkPanelLayout.globalSlots(
			overlay.getBookmarkList().getDisplaySlots(contents.getUsableColumnCount(), contents.getUsableColumnsPerRow()),
			rows
		);
		this.start = globalRow(startSlot);
	}

	public boolean isDropMode() {
		return dropMode;
	}

	/**
	 * Ported from 1.21.1 fork (commit 29334012f): the cross-page-aware preview.
	 * Walks the visible group panel slots and recolors each one based on whether
	 * its item is in the "selected" set (will be moved into the dragged group),
	 * the "released" set (will be moved out of the group because the drag
	 * shortened the group on this side), or neither (keep its current group).
	 */
	public List<GroupPanelSlot> getPreviewGroupPanelSlots(List<GroupPanelSlot> groupPanelSlots) {
		if (dropMode) {
			return groupPanelSlots;
		}
		updateEndSlot(MouseUtil.getY());
		if (this.endSlot == null) {
			return groupPanelSlots;
		}

		return groupPanelSlots.stream()
			.map(row -> new GroupPanelSlot(row.bookmark(),
				previewGroup(row.bookmark(), row.groupId()), row.area()))
			.toList();
	}

	private int previewGroup(IBookmark item, int groupId) {
		if (selected.contains(item)) {
			return exclude ? BookmarkGroupManager.DEFAULT_GROUP_ID
				: (start.groupId() == BookmarkGroupManager.DEFAULT_GROUP_ID ? GROUPING_PREVIEW_GROUP_ID : start.groupId());
		}
		return released.contains(item) ? BookmarkGroupManager.DEFAULT_GROUP_ID : groupId;
	}

	/**
	 * Ported from 1.21.1 fork (commit 29334012f): the renderer asks this when
	 * drawing a drag preview so that the boundary connection at the visible-page
	 * seam can be recomputed against the cross-page slots.
	 */
	BookmarkOverlayLayout.BoundaryConnections boundaries(List<GroupPanelSlot> visible) {
		if (plan == null || visible.isEmpty()) {
			return BookmarkOverlayLayout.BoundaryConnections.NONE;
		}
		int first = globalRow(visible.get(0)).area().getY();
		int last = globalRow(visible.get(visible.size() - 1)).area().getY();
		var before = slots.stream()
			.filter(slot -> slot.area().getY() < first)
			.reduce((a, b) -> b);
		var after = slots.stream()
			.filter(slot -> slot.area().getY() > last)
			.findFirst();
		return new BookmarkOverlayLayout.BoundaryConnections(
			visible.get(0).groupId() != BookmarkGroupManager.DEFAULT_GROUP_ID && before
				.map(slot -> previewGroup(slot.item(), slot.groupId()) == visible.get(0).groupId())
				.orElse(false),
			visible.get(visible.size() - 1).groupId() != BookmarkGroupManager.DEFAULT_GROUP_ID && after
				.map(slot -> previewGroup(slot.item(), slot.groupId()) == visible.get(visible.size() - 1).groupId())
				.orElse(false));
	}

	public boolean complete(UserInput input) {
		if (dropMode) {
			if (!isDragged(input)) {
				if (clickFallbackAction == null) {
					return false;
				}
				boolean changed = overlay.getInputHandlers().applyGroupClickAction(startSlot.groupId(), clickFallbackAction);
				if (changed) {
					BookmarkOverlay.playClickSound();
				}
				return changed;
			}
			BookmarkGroupDropBridge.GroupDropHandler handler = BookmarkGroupDropBridge.getGroupDropHandler();
			if (handler != null) {
				List<ITypedIngredient<?>> ingredients = BookmarkGroupDropBridge.getGroupDropIngredients(overlay.getBookmarkList(), startSlot.groupId());
				if (!ingredients.isEmpty() && handler.dropGroup(ingredients, input.getMouseX(), input.getMouseY())) {
					BookmarkOverlay.playClickSound();
					return true;
				}
			}
			return false;
		}
		updateEndSlot(input.getMouseY());
		if (this.endSlot == null) {
			if (this.clickFallbackAction == null) {
				return false;
			}
			if (input.isSimulate()) {
				return true;
			}
			boolean changed = overlay.getInputHandlers().applyGroupClickAction(startSlot.groupId(), this.clickFallbackAction);
			if (changed) {
				BookmarkOverlay.playClickSound();
			}
			return changed;
		}
		if (plan == null || plan.bookmarks().isEmpty()) {
			return false;
		}
		boolean changed = input.isSimulate() || plan.apply(overlay.getBookmarkList(), "Group");
		if (changed && !input.isSimulate()) {
			BookmarkOverlay.playClickSound();
		}
		return changed;
	}

	void drawPreview(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (!dropMode || !isDragged(mouseX, mouseY)) {
			return;
		}
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> groupSlots = overlay.getPanelSlots().stream()
			.filter(slot -> startSlot.groupId() == overlay.getBookmarkList().getBookmarkGroupId(slot.item()))
			.toList();
		if (groupSlots.isEmpty()) {
			return;
		}
		ImmutableRect2i origin = groupSlots.get(0).area();
		List<BookmarkSortDragState.PreviewSlot<IBookmark>> allPreviewSlots = BookmarkSortDragState.toPreviewSlots(groupSlots, origin);
		List<BookmarkSortDragState.PreviewSlot<IBookmark>> dropPreviewSlots = BookmarkSortDragState.toPreviewSlots(
			BookmarkGroupDropBridge.getGroupDropPanelSlots(overlay.getBookmarkList(), groupSlots, startSlot.groupId()),
			origin
		);
		if (dropPreviewSlots.isEmpty()) {
			return;
		}
		IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
		BookmarkGroupDropBridge.GroupDropHighlightProvider highlightProvider = BookmarkGroupDropBridge.getGroupDropHighlightProvider();
		if (highlightProvider != null && !overlay.getToggleState().isCheatItemsEnabled()) {
			List<ITypedIngredient<?>> ingredients = new ArrayList<>(dropPreviewSlots.size());
			for (BookmarkSortDragState.PreviewSlot<IBookmark> slot : dropPreviewSlots) {
				ingredients.add(slot.element().getElement().getTypedIngredient());
			}
			List<Rect2i> dropAreas = highlightProvider.getDropAreas(ingredients);
			GhostIngredientDrag.drawTargets(guiGraphics, mouseX, mouseY, dropAreas);
		}
		for (BookmarkSortDragState.PreviewSlot<IBookmark> slot : dropPreviewSlots) {
			ITypedIngredient<?> ingredient = slot.element().getElement().getTypedIngredient();
			BookmarkSortDragState.drawIngredient(
				guiGraphics,
				ingredientManager,
				ingredient,
				mouseX + dragOffsetX + slot.relativeX(),
				mouseY + dragOffsetY + slot.relativeY()
			);
		}
		List<BookmarkSortDragState.FloatingGroupPanelSlot> floatingSlots = BookmarkSortDragState.getFloatingGroupPanelSlots(
			allPreviewSlots,
			mouseX,
			mouseY,
			dragOffsetX,
			dragOffsetY
		);
		int color = overlay.getRenderer().getGroupPanelColor(startSlot.groupId());
		for (BookmarkSortDragState.FloatingGroupPanelSlot slot : floatingSlots) {
			overlay.getRenderer().drawGroupPanelLine(
				guiGraphics,
				slot.slotArea(),
				color,
				slot.connectedToPrevious(),
				slot.connectedToNext()
			);
		}
	}

	private boolean isDragged(UserInput input) {
		return isDragged(input.getMouseX(), input.getMouseY());
	}

	private boolean isDragged(double mouseX, double mouseY) {
		return Math.abs(mouseX - startX) > 4 || Math.abs(mouseY - startY) > 4;
	}

	private void updateEndSlot(double mouseY) {
		Optional<GroupPanelSlot> hoveredSlot = overlay.getClosestGroupPanelSlotAtY(mouseY);
		if (hoveredSlot.isEmpty()) {
			return;
		}
		GroupPanelSlot currentEndSlot = hoveredSlot.get();
		BookmarkPanelLayout.RowSlot<IBookmark> end = globalRow(currentEndSlot);
		long elapsedMillis = System.currentTimeMillis() - startedAtMillis;
		// Ported from 1.21.1 fork (commit 29334012f): the OR with the global-row
		// change lets the plan recompute when the user scrolls across a page
		// boundary, even if the visible-slot Y happens to match the previous
		// selection (the global Y moves when the visible-page index changes).
		if (end.area().getY() != start.area().getY() || BookmarkPanelLayout.shouldUpdateDragEnd(
			BookmarkOverlayLayout.toRowSlot(startSlot),
			this.endSlot == null ? null : BookmarkOverlayLayout.toRowSlot(this.endSlot),
			mouseY,
			elapsedMillis,
			GROUP_PANEL_DRAG_THRESHOLD_MS
		)) {
			if (endSlot == null || !end.equals(globalEnd)) {
				plan = BookmarkGroupingPlan.create(slots, start, end, exclude);
				selected = new HashSet<>(plan.bookmarks());
				released = new HashSet<>(plan.releasedBookmarks());
				globalEnd = end;
			}
			this.endSlot = currentEndSlot;
		}
	}

	private BookmarkPanelLayout.RowSlot<IBookmark> globalRow(GroupPanelSlot slot) {
		IngredientGridWithNavigation contents = overlay.getContents();
		List<IngredientListSlot> visible = contents.getSlots().toList();
		int index = 0;
		while (index < visible.size() && visible.get(index).getArea().getY() != slot.area().getY()) {
			index++;
		}
		int row = BookmarkRowLayout.rowStart(contents.getFirstItemIndex() + index, rows);
		return new BookmarkPanelLayout.RowSlot<>(slot.bookmark(), slot.groupId(), new ImmutableRect2i(0, row, 1, 1));
	}

	void scroll(double deltaX, double deltaY, double mouseY) {
		IngredientGridWithNavigation contents = overlay.getContents();
		ImmutableRect2i area = contents.getIngredientGridArea();
		if (!area.isEmpty()) {
			contents.createInputHandler().handleMouseScrolled(area.getX() + 1, area.getY() + 1, deltaY);
		}
		updateEndSlot(mouseY);
	}
}

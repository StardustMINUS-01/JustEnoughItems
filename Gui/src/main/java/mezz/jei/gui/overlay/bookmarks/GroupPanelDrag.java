package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.ghost.GhostIngredientDrag;
import mezz.jei.gui.input.MouseUtil;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlayLayout.GroupPanelSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
	}

	public boolean isDropMode() {
		return dropMode;
	}

	public List<GroupPanelSlot> getPreviewGroupPanelSlots(
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		List<GroupPanelSlot> groupPanelSlots
	) {
		if (dropMode) {
			return groupPanelSlots;
		}
		updateEndSlot(MouseUtil.getY());
		if (this.endSlot == null) {
			return groupPanelSlots;
		}

		List<BookmarkPanelLayout.RowSlot<IBookmark>> previewRows = BookmarkPanelLayout.createGroupingPreviewRows(
			panelSlots,
			BookmarkOverlayLayout.toRowSlots(groupPanelSlots),
			BookmarkOverlayLayout.toRowSlot(startSlot),
			BookmarkOverlayLayout.toRowSlot(this.endSlot),
			exclude,
			GROUPING_PREVIEW_GROUP_ID
		);
		return previewRows.stream()
			.map(overlay::toGroupPanelSlot)
			.toList();
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
		BookmarkGroupingPlan plan = BookmarkGroupingPlan.create(
			overlay.getPanelSlots(),
			BookmarkOverlayLayout.toRowSlot(startSlot),
			BookmarkOverlayLayout.toRowSlot(this.endSlot),
			exclude
		);
		if (plan.bookmarks().isEmpty()) {
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
		ImmutableRect2i origin = groupSlots.getFirst().area();
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
		long elapsedMillis = System.currentTimeMillis() - startedAtMillis;
		if (BookmarkPanelLayout.shouldUpdateDragEnd(
			BookmarkOverlayLayout.toRowSlot(startSlot),
			this.endSlot == null ? null : BookmarkOverlayLayout.toRowSlot(this.endSlot),
			BookmarkOverlayLayout.toRowSlot(currentEndSlot),
			elapsedMillis,
			GROUP_PANEL_DRAG_THRESHOLD_MS
		)) {
			this.endSlot = currentEndSlot;
		}
	}
}

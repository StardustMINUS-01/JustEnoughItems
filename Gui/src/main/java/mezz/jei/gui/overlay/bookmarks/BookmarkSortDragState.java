package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.SafeIngredientUtil;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkMoveSelection;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.IBookmark;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class BookmarkSortDragState {
	private static final int DRAG_OVERLAY_COLOR = 0x66555555;

	private final Kind kind;
	private final @Nullable IBookmark sourceBookmark;
	private final String sourceGroupId;
	private final ImmutableRect2i sourceArea;
	private final int dragOffsetX;
	private final int dragOffsetY;
	private final long startedAtMillis;
	private final long thresholdMillis;
	private final Set<IBookmark> hiddenBookmarks = new HashSet<>();
	private List<PreviewSlot> previewSlots = List.of();
	private List<ImmutableRect2i> targetSlotOverlayAreas = List.of();
	private boolean active;

	private BookmarkSortDragState(
		Kind kind,
		@Nullable IBookmark sourceBookmark,
		String sourceGroupId,
		ImmutableRect2i sourceArea,
		int dragOffsetX,
		int dragOffsetY,
		long startedAtMillis,
		long thresholdMillis,
		boolean active
	) {
		this.kind = kind;
		this.sourceBookmark = sourceBookmark;
		this.sourceGroupId = sourceGroupId;
		this.sourceArea = sourceArea;
		this.dragOffsetX = dragOffsetX;
		this.dragOffsetY = dragOffsetY;
		this.startedAtMillis = startedAtMillis;
		this.thresholdMillis = thresholdMillis;
		this.active = active;
	}

	public static BookmarkSortDragState item(
		IBookmark sourceBookmark,
		ImmutableRect2i sourceArea,
		double mouseX,
		double mouseY,
		long startedAtMillis,
		long thresholdMillis
	) {
		return new BookmarkSortDragState(
			Kind.ITEM,
			sourceBookmark,
			"",
			sourceArea,
			sourceArea.getX() - (int) Math.round(mouseX),
			sourceArea.getY() - (int) Math.round(mouseY),
			startedAtMillis,
			thresholdMillis,
			!sourceArea.contains(mouseX, mouseY)
		);
	}

	public static BookmarkSortDragState group(String sourceGroupId) {
		return group(sourceGroupId, ImmutableRect2i.EMPTY, 0, 0);
	}

	public static BookmarkSortDragState group(String sourceGroupId, ImmutableRect2i sourceArea, double mouseX, double mouseY) {
		return new BookmarkSortDragState(
			Kind.GROUP,
			null,
			sourceGroupId,
			sourceArea,
			sourceArea.getX() - (int) Math.round(mouseX),
			sourceArea.getY() - (int) Math.round(mouseY),
			0,
			0,
			true
		);
	}

	public boolean isActive() {
		return active;
	}

	public String getSourceGroupId() {
		return sourceGroupId;
	}

	public static int getDragOverlayColor() {
		return DRAG_OVERLAY_COLOR;
	}

	public GroupPanelRenderMode getGroupPanelRenderMode(String groupId) {
		if (kind == Kind.GROUP && active && !hiddenBookmarks.isEmpty() && sourceGroupId.equals(groupId)) {
			return GroupPanelRenderMode.DRAG_PLACEHOLDER;
		}
		return GroupPanelRenderMode.NORMAL;
	}

	public List<ImmutableRect2i> getSourceSlotOverlayAreas(List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots) {
		if (kind != Kind.GROUP || !active || hiddenBookmarks.isEmpty()) {
			return List.of();
		}
		return panelSlots.stream()
			.filter(slot -> hiddenBookmarks.contains(slot.item()))
			.map(BookmarkPanelLayout.PanelSlot::area)
			.toList();
	}

	public List<ImmutableRect2i> getSourceGroupPanelOverlayAreas(
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		int groupPanelWidth
	) {
		if (kind != Kind.GROUP || !active || hiddenBookmarks.isEmpty()) {
			return List.of();
		}
		return panelSlots.stream()
			.filter(slot -> hiddenBookmarks.contains(slot.item()))
			.map(BookmarkPanelLayout.PanelSlot::area)
			.map(area -> BookmarkPanelLayout.getGroupPanelArea(area, groupPanelWidth))
			.toList();
	}

	public boolean drawSourceSlotOverlays(GuiGraphics guiGraphics, List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots) {
		List<ImmutableRect2i> areas = getSourceSlotOverlayAreas(panelSlots);
		for (ImmutableRect2i area : areas) {
			guiGraphics.fill(
				area.getX(),
				area.getY(),
				area.getX() + area.getWidth(),
				area.getY() + area.getHeight(),
				DRAG_OVERLAY_COLOR
			);
		}
		return !areas.isEmpty();
	}

	public List<ImmutableRect2i> getTargetSlotOverlayAreas() {
		if (!active) {
			return List.of();
		}
		return targetSlotOverlayAreas;
	}

	public void updateProjectedSlotOverlayAreas(List<BookmarkPanelLayout.PanelSlot<IBookmark>> projectedPanelSlots) {
		if (!active || hiddenBookmarks.isEmpty()) {
			targetSlotOverlayAreas = List.of();
			return;
		}
		targetSlotOverlayAreas = projectedPanelSlots.stream()
			.filter(slot -> hiddenBookmarks.contains(slot.item()))
			.map(BookmarkPanelLayout.PanelSlot::area)
			.toList();
	}

	public boolean drawTargetSlotOverlays(GuiGraphics guiGraphics) {
		List<ImmutableRect2i> areas = getTargetSlotOverlayAreas();
		for (ImmutableRect2i area : areas) {
			guiGraphics.fill(
				area.getX(),
				area.getY(),
				area.getX() + area.getWidth(),
				area.getY() + area.getHeight(),
				DRAG_OVERLAY_COLOR
			);
		}
		return !areas.isEmpty();
	}

	public void stop() {
		for (IBookmark bookmark : hiddenBookmarks) {
			bookmark.setVisible(true);
		}
		hiddenBookmarks.clear();
		targetSlotOverlayAreas = List.of();
		active = false;
	}

	public boolean drawDraggedItems(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (!active || previewSlots.isEmpty()) {
			return false;
		}
		IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
		for (PreviewSlot slot : previewSlots) {
			ITypedIngredient<?> typedIngredient = slot.bookmark().getElement().getTypedIngredient();
			drawIngredient(
				guiGraphics,
				ingredientManager,
				typedIngredient,
				mouseX + dragOffsetX + slot.relativeX(),
				mouseY + dragOffsetY + slot.relativeY()
			);
		}
		return true;
	}

	public List<FloatingGroupPanelSlot> getFloatingGroupPanelSlots(int mouseX, int mouseY) {
		if (kind != Kind.GROUP || !active || previewSlots.isEmpty()) {
			return List.of();
		}
		return getFloatingGroupPanelSlots(previewSlots, mouseX, mouseY, dragOffsetX, dragOffsetY);
	}

	private static List<FloatingGroupPanelSlot> getFloatingGroupPanelSlots(
		List<PreviewSlot> previewSlots,
		int mouseX,
		int mouseY,
		int dragOffsetX,
		int dragOffsetY
	) {
		Map<Integer, PreviewSlot> rowsByY = new LinkedHashMap<>();
		previewSlots.stream()
			.sorted(Comparator.comparingInt(PreviewSlot::relativeY).thenComparingInt(PreviewSlot::relativeX))
			.forEach(slot -> rowsByY.putIfAbsent(slot.relativeY(), slot));
		List<PreviewSlot> rowSlots = new ArrayList<>(rowsByY.values());
		int groupLeftX = previewSlots.stream()
			.mapToInt(PreviewSlot::relativeX)
			.min()
			.orElse(0);
		List<FloatingGroupPanelSlot> groupPanelSlots = new ArrayList<>();
		for (int i = 0; i < rowSlots.size(); i++) {
			PreviewSlot slot = rowSlots.get(i);
			boolean connectedToPrevious = i > 0 && rowSlots.get(i - 1).relativeY() + rowSlots.get(i - 1).height() == slot.relativeY();
			boolean connectedToNext = i + 1 < rowSlots.size() && slot.relativeY() + slot.height() == rowSlots.get(i + 1).relativeY();
			groupPanelSlots.add(new FloatingGroupPanelSlot(
				new ImmutableRect2i(
					mouseX + dragOffsetX + groupLeftX,
					mouseY + dragOffsetY + slot.relativeY(),
					slot.width(),
					slot.height()
				),
				connectedToPrevious,
				connectedToNext
			));
		}
		return groupPanelSlots;
	}

	public boolean update(
		BookmarkList bookmarkList,
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		double mouseX,
		double mouseY,
		long nowMillis
	) {
		return update(bookmarkList, panelSlots, panelSlots, mouseX, mouseY, nowMillis);
	}

	public boolean update(
		BookmarkList bookmarkList,
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> visiblePanelSlots,
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> projectedPanelSlots,
		double mouseX,
		double mouseY,
		long nowMillis
	) {
		return switch (kind) {
			case ITEM -> updateItem(bookmarkList, visiblePanelSlots, projectedPanelSlots, mouseX, mouseY, nowMillis);
			case GROUP -> updateGroup(bookmarkList, projectedPanelSlots, mouseY);
		};
	}

	private boolean updateItem(
		BookmarkList bookmarkList,
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> visiblePanelSlots,
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> projectedPanelSlots,
		double mouseX,
		double mouseY,
		long nowMillis
	) {
		if (!active) {
			active = !sourceArea.contains(mouseX, mouseY) || nowMillis - startedAtMillis >= thresholdMillis;
			if (!active) {
				return false;
			}
		}
		if (sourceBookmark == null) {
			return false;
		}
		BookmarkMoveSelection selection = BookmarkMoveSelection.create(bookmarkList, sourceBookmark);
		hide(selection.bookmarks(), visiblePanelSlots, sourceArea);

		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots = BookmarkPanelLayout.createRowSlots(projectedPanelSlots);
		String currentSourceGroupId = bookmarkList.getBookmarkGroupId(sourceBookmark);
		Optional<BookmarkPanelLayout.PanelSlot<IBookmark>> targetSlot = findSlotAt(projectedPanelSlots, mouseX, mouseY);
		if (selection.crossGroupMove()) {
			return updateCrossGroupItem(bookmarkList, selection, currentSourceGroupId, projectedPanelSlots, rowSlots, targetSlot, mouseY);
		}
		return updateConcreteSlotItem(bookmarkList, selection, currentSourceGroupId, targetSlot);
	}

	private boolean updateCrossGroupItem(
		BookmarkList bookmarkList,
		BookmarkMoveSelection selection,
		String currentSourceGroupId,
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> projectedPanelSlots,
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		Optional<BookmarkPanelLayout.PanelSlot<IBookmark>> targetSlot,
		double mouseY
	) {
		if (sourceBookmark == null) {
			targetSlotOverlayAreas = List.of();
			return false;
		}
		Optional<BookmarkPanelLayout.PanelSlot<IBookmark>> firstColumnSlot = findFirstColumnSlotAtY(projectedPanelSlots, mouseY);
		if (firstColumnSlot.isPresent()) {
			BookmarkPanelLayout.PanelSlot<IBookmark> slot = firstColumnSlot.get();
			if (currentSourceGroupId.equals(slot.groupId()) && !selection.bookmarks().contains(slot.item())) {
				return updateConcreteSlotItem(bookmarkList, selection, currentSourceGroupId, firstColumnSlot);
			}
			if (selection.bookmarks().contains(slot.item())) {
				targetSlotOverlayAreas = List.of();
				return false;
			}
			Optional<BookmarkPanelLayout.RowSlot<IBookmark>> targetRow = findRowByY(rowSlots, slot.area().getY());
			if (targetRow.isEmpty()) {
				targetSlotOverlayAreas = List.of();
				return false;
			}
			BookmarkItemMovePlan plan = BookmarkItemMovePlan.createCrossGroup(
				projectedPanelSlots,
				rowSlots,
				targetRow.get(),
				mouseY,
				bookmarkList.getBookmarkGroups().stream()
					.collect(Collectors.toMap(BookmarkGroup::id, Function.identity())),
				selection.recipeIds(),
				getRecipeIdsByGroup(bookmarkList, selection.bookmarks())
			);
			if (plan.rejected() || selection.bookmarks().contains(plan.targetBookmark())) {
				targetSlotOverlayAreas = List.of();
				return false;
			}
			selection.moveToBookmark(bookmarkList, plan.targetBookmark(), plan.targetGroupId(), plan.offset());
			return true;
		}
		Optional<BookmarkPanelLayout.RowSlot<IBookmark>> targetRow = targetSlot
			.flatMap(slot -> findRowByY(rowSlots, slot.area().getY()))
			.filter(row -> currentSourceGroupId.equals(row.groupId()));
		if (targetRow.isEmpty() || targetSlot.filter(slot -> selection.bookmarks().contains(slot.item())).isPresent()) {
			targetSlotOverlayAreas = List.of();
			return false;
		}
		BookmarkItemMovePlan plan = BookmarkItemMovePlan.createSameGroupByRow(
			projectedPanelSlots,
			rowSlots,
			sourceBookmark,
			targetRow.get(),
			bookmarkList.getBookmarks(),
			mouseY
		);
		if (selection.bookmarks().contains(plan.targetBookmark())) {
			targetSlotOverlayAreas = List.of();
			return false;
		}
		selection.moveToBookmark(bookmarkList, plan.targetBookmark(), plan.targetGroupId(), plan.offset());
		return true;
	}

	private static Optional<BookmarkPanelLayout.PanelSlot<IBookmark>> findSlotAt(
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		double mouseX,
		double mouseY
	) {
		return panelSlots.stream()
			.filter(slot -> slot.area().contains(mouseX, mouseY))
			.findFirst();
	}

	private static Optional<BookmarkPanelLayout.PanelSlot<IBookmark>> findFirstColumnSlotAtY(
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		double mouseY
	) {
		Optional<Integer> gridLeftX = panelSlots.stream()
			.map(BookmarkPanelLayout.PanelSlot::area)
			.mapToInt(ImmutableRect2i::getX)
			.min()
			.stream()
			.boxed()
			.findFirst();
		if (gridLeftX.isEmpty()) {
			return Optional.empty();
		}
		return findSlotAt(panelSlots, gridLeftX.get(), mouseY);
	}

	private static Optional<BookmarkPanelLayout.RowSlot<IBookmark>> findRowByY(
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		int y
	) {
		return rowSlots.stream()
			.filter(row -> row.area().getY() == y)
			.findFirst();
	}

	private boolean updateConcreteSlotItem(
		BookmarkList bookmarkList,
		BookmarkMoveSelection selection,
		String currentSourceGroupId,
		Optional<BookmarkPanelLayout.PanelSlot<IBookmark>> targetSlot
	) {
		if (sourceBookmark == null || targetSlot.isEmpty()) {
			targetSlotOverlayAreas = List.of();
			return false;
		}
		IBookmark targetBookmark = targetSlot.get().item();
		if (targetBookmark.equals(sourceBookmark) || selection.bookmarks().contains(targetBookmark)) {
			targetSlotOverlayAreas = List.of();
			return false;
		}
		if (!currentSourceGroupId.equals(targetSlot.get().groupId())) {
			targetSlotOverlayAreas = List.of();
			return false;
		}
		if (!canSortOverConcreteTarget(bookmarkList, sourceBookmark, targetBookmark)) {
			targetSlotOverlayAreas = List.of();
			return false;
		}
		int offset = getConcreteSlotOffset(bookmarkList, sourceBookmark, targetBookmark);
		selection.moveToBookmark(bookmarkList, targetBookmark, currentSourceGroupId, offset);
		return true;
	}

	private static boolean usesConcreteSlotSort(
		BookmarkList bookmarkList,
		IBookmark sourceBookmark,
		BookmarkMoveSelection selection
	) {
		if (selection.movesRecipe()) {
			return false;
		}
		BookmarkItemMetadata sourceMetadata = bookmarkList.getBookmarkMetadata(sourceBookmark);
		return sourceMetadata.recipeUid() != null && !sourceMetadata.type().isGraphOutput();
	}

	private static boolean canSortOverConcreteTarget(BookmarkList bookmarkList, IBookmark sourceBookmark, IBookmark targetBookmark) {
		BookmarkItemMetadata sourceMetadata = bookmarkList.getBookmarkMetadata(sourceBookmark);
		BookmarkItemMetadata targetMetadata = bookmarkList.getBookmarkMetadata(targetBookmark);
		if (sourceMetadata.type().recipeRole() == RecipeIngredientRole.INPUT && targetMetadata.type().isGraphOutput()) {
			return false;
		}
		return canSortOverTarget(bookmarkList, sourceBookmark, targetBookmark);
	}

	private static int getConcreteSlotOffset(BookmarkList bookmarkList, IBookmark sourceBookmark, IBookmark targetBookmark) {
		List<IBookmark> bookmarks = bookmarkList.getBookmarks();
		int sourceIndex = bookmarks.indexOf(sourceBookmark);
		int targetIndex = bookmarks.indexOf(targetBookmark);
		if (sourceIndex >= 0 && targetIndex >= 0 && sourceIndex < targetIndex) {
			return 1;
		}
		return 0;
	}

	private static boolean canSortOverTarget(BookmarkList bookmarkList, IBookmark sourceBookmark, IBookmark targetBookmark) {
		BookmarkItemMetadata sourceMetadata = bookmarkList.getBookmarkMetadata(sourceBookmark);
		if (sourceMetadata.type().isGraphOutput() || sourceMetadata.recipeUid() == null) {
			return true;
		}
		boolean todoGroup = bookmarkList.getBookmarkGroups().stream()
			.filter(group -> group.id().equals(sourceMetadata.groupId()))
			.findFirst()
			.map(group -> group.viewMode() == BookmarkViewMode.TODO_LIST)
			.orElse(false);
		if (!todoGroup) {
			return true;
		}
		BookmarkItemMetadata targetMetadata = bookmarkList.getBookmarkMetadata(targetBookmark);
		return targetMetadata.equalsRecipe(sourceMetadata.recipeUid(), sourceMetadata.groupId());
	}

	private boolean updateGroup(
		BookmarkList bookmarkList,
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		double mouseY
	) {
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots = BookmarkPanelLayout.createRowSlots(panelSlots);
		Optional<BookmarkPanelLayout.RowSlot<IBookmark>> sourceRow = rowSlots.stream()
			.filter(row -> sourceGroupId.equals(row.groupId()))
			.findFirst();
		if (sourceRow.isEmpty()) {
			return false;
		}
		List<IBookmark> groupBookmarks = bookmarkList.getBookmarks().stream()
			.filter(bookmark -> sourceGroupId.equals(bookmarkList.getBookmarkGroupId(bookmark)))
			.toList();
		ImmutableRect2i previewOrigin = sourceRow.get().area();
		hide(groupBookmarks, panelSlots, previewOrigin);

		Optional<BookmarkPanelLayout.RowSlot<IBookmark>> targetRow = BookmarkPanelLayout.findClosestRowAtY(rowSlots, mouseY);
		if (targetRow.isEmpty() || sourceGroupId.equals(targetRow.get().groupId())) {
			return false;
		}
		return BookmarkGroupMovePlan.create(rowSlots, sourceRow.get(), targetRow.get()).apply(bookmarkList);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <T> void drawIngredient(
		GuiGraphics guiGraphics,
		IIngredientManager ingredientManager,
		ITypedIngredient<T> typedIngredient,
		int x,
		int y
	) {
		IIngredientType<T> type = typedIngredient.getType();
		IIngredientRenderer<T> renderer = ingredientManager.getIngredientRenderer(type);
		SafeIngredientUtil.render(guiGraphics, renderer, typedIngredient, x, y);
	}

	private void hide(
		List<IBookmark> bookmarks,
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		ImmutableRect2i previewOrigin
	) {
		if (previewSlots.isEmpty()) {
			Set<IBookmark> bookmarkSet = new HashSet<>(bookmarks);
			previewSlots = panelSlots.stream()
				.filter(slot -> bookmarkSet.contains(slot.item()))
				.map(slot -> new PreviewSlot(
					slot.item(),
					slot.area().getX() - previewOrigin.getX(),
					slot.area().getY() - previewOrigin.getY(),
					slot.area().getWidth(),
					slot.area().getHeight()
				))
				.toList();
		}
		for (IBookmark bookmark : bookmarks) {
			if (hiddenBookmarks.add(bookmark)) {
				bookmark.setVisible(false);
			}
		}
	}

	public record FloatingGroupPanelSlot(ImmutableRect2i slotArea, boolean connectedToPrevious, boolean connectedToNext) {
	}

	public enum GroupPanelRenderMode {
		NORMAL,
		DRAG_PLACEHOLDER
	}

	private record PreviewSlot(IBookmark bookmark, int relativeX, int relativeY, int width, int height) {
	}

	private static Map<String, Set<ResourceLocation>> getRecipeIdsByGroup(
		BookmarkList bookmarkList,
		List<IBookmark> ignoredBookmarks
	) {
		Set<IBookmark> ignored = new HashSet<>(ignoredBookmarks);
		Map<String, Set<ResourceLocation>> recipeIdsByGroup = new HashMap<>();
		for (IBookmark bookmark : bookmarkList.getBookmarks()) {
			if (ignored.contains(bookmark)) {
				continue;
			}
			BookmarkItemMetadata metadata = bookmarkList.getBookmarkMetadata(bookmark);
			ResourceLocation recipeUid = metadata.recipeUid();
			if (recipeUid != null && metadata.type().isRecipeAssociated()) {
				recipeIdsByGroup.computeIfAbsent(metadata.groupId(), groupId -> new HashSet<>())
					.add(recipeUid);
			}
		}
		return recipeIdsByGroup;
	}

	private enum Kind {
		ITEM,
		GROUP
	}
}

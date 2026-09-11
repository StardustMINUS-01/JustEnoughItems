package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkDisplaySlot;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class BookmarkPanelLayout {
	private BookmarkPanelLayout() {
	}

	public record PanelSlot<T>(
		T item,
		int groupId,
		ImmutableRect2i area,
		boolean shadow,
		@Nullable Object recipeKey
	) {
		public PanelSlot(T item, int groupId, ImmutableRect2i area, boolean shadow) {
			this(item, groupId, area, shadow, null);
		}
	}

	public record RowSlot<T>(
		T item,
		int groupId,
		ImmutableRect2i area
	) {
	}

	public record RecipeBoundaryInsertionTarget<T>(
		T item,
		int groupId,
		ImmutableRect2i area,
		int offset
	) {
	}

	public static <T> List<RowSlot<T>> createRowSlots(List<PanelSlot<T>> panelSlots) {
		int gridLeftX = panelSlots.stream()
			.map(PanelSlot::area)
			.mapToInt(ImmutableRect2i::getX)
			.min()
			.orElse(0);
		return createRowSlots(panelSlots, gridLeftX);
	}

	public static <T> List<RowSlot<T>> createRowSlots(List<PanelSlot<T>> panelSlots, int gridLeftX) {
		List<PanelSlot<T>> sortedSlots = panelSlots.stream()
			.sorted(Comparator.comparingInt((PanelSlot<T> slot) -> slot.area().getY()).thenComparingInt(slot -> slot.area().getX()))
			.toList();
		List<RowSlot<T>> rowSlots = new ArrayList<>();
		for (PanelSlot<T> panelSlot : sortedSlots) {
			Optional<RowSlot<T>> rowSlot = rowSlots.stream()
				.filter(slot -> slot.area().getY() == panelSlot.area().getY())
				.findFirst();
			if (rowSlot.isEmpty()) {
				rowSlots.add(new RowSlot<>(panelSlot.item(), panelSlot.groupId(), withGridLeft(panelSlot.area(), gridLeftX)));
			} else if (rowSlot.get().groupId() == BookmarkGroupManager.DEFAULT_GROUP_ID &&
				!(panelSlot.groupId() == BookmarkGroupManager.DEFAULT_GROUP_ID)) {
				int index = rowSlots.indexOf(rowSlot.get());
				RowSlot<T> current = rowSlot.get();
				rowSlots.set(index, new RowSlot<>(current.item(), panelSlot.groupId(), current.area()));
			}
		}
		return rowSlots;
	}

	public static <T> List<PanelSlot<T>> createPagePanelSlots(
		List<BookmarkDisplaySlot<T>> displaySlots,
		List<ImmutableRect2i> pageAreas,
		int firstDisplaySlotIndex
	) {
		List<PanelSlot<T>> panelSlots = new ArrayList<>();
		for (BookmarkDisplaySlot<T> displaySlot : displaySlots) {
			int pageIndex = displaySlot.slotIndex() - firstDisplaySlotIndex;
			if (pageIndex < 0 || pageIndex >= pageAreas.size()) {
				continue;
			}
			BookmarkItemMetadata metadata = displaySlot.entry().metadata();
			Object recipeKey = metadata.type().isRecipeAssociated() ?
				displaySlot.entry().displayRecipeUid().orElse(metadata.recipeUid()) : null;
			panelSlots.add(new PanelSlot<>(
				displaySlot.entry().item(),
				metadata.groupId(),
				pageAreas.get(pageIndex),
				displaySlot.shadow(),
				recipeKey
			));
		}
		return List.copyOf(panelSlots);
	}

	private static ImmutableRect2i withGridLeft(ImmutableRect2i area, int gridLeftX) {
		return new ImmutableRect2i(gridLeftX, area.getY(), area.getWidth(), area.getHeight());
	}

	public static ImmutableRect2i getGroupPanelArea(ImmutableRect2i slotArea, int groupPanelWidth) {
		return new ImmutableRect2i(slotArea.getX() - groupPanelWidth, slotArea.getY(), groupPanelWidth, slotArea.getHeight());
	}

	public static ImmutableRect2i getGroupPanelPlaceholderLineArea(
		ImmutableRect2i slotArea,
		boolean connectedToPrevious,
		boolean connectedToNext,
		int groupPanelWidth
	) {
		int x = slotArea.getX() - groupPanelWidth / 2 - 1;
		int top = connectedToPrevious ? slotArea.getY() : slotArea.getY() + 2;
		int bottom = connectedToNext ? slotArea.getY() + slotArea.getHeight() : slotArea.getY() + slotArea.getHeight() - 2;
		return new ImmutableRect2i(x, top, 1, bottom - top);
	}

	public static <T> Optional<RowSlot<T>> findRowUnderMouse(
		List<RowSlot<T>> rowSlots,
		double mouseX,
		double mouseY,
		int groupPanelWidth
	) {
		return rowSlots.stream()
			.filter(slot -> getGroupPanelArea(slot.area(), groupPanelWidth).contains(mouseX, mouseY))
			.findFirst();
	}

	public static <T> Optional<RowSlot<T>> findClosestRowAtY(List<RowSlot<T>> rowSlots, double mouseY) {
		if (rowSlots.isEmpty()) {
			return Optional.empty();
		}
		int firstY = rowSlots.get(0).area().getY();
		int rowHeight = rowSlots.get(0).area().getHeight();
		int rowIndex = (int) Math.floor((mouseY - firstY) / rowHeight);
		int clampedRowIndex = Math.max(0, Math.min(rowIndex, rowSlots.size() - 1));
		return Optional.of(rowSlots.get(clampedRowIndex));
	}

	public static <T> Optional<RecipeBoundaryInsertionTarget<T>> getRecipeBoundaryInsertionTarget(
		List<PanelSlot<T>> panelSlots,
		RowSlot<T> targetRow,
		double mouseY
	) {
		Optional<PanelSlot<T>> anchor = panelSlots.stream()
			.filter(slot -> slot.area().getY() == targetRow.area().getY())
			.filter(slot -> slot.recipeKey() != null)
			.min(Comparator.comparingInt(slot -> slot.area().getX()));
		if (anchor.isEmpty()) {
			return Optional.empty();
		}

		PanelSlot<T> anchorSlot = anchor.get();
		List<PanelSlot<T>> recipeSlots = panelSlots.stream()
			.filter(slot -> anchorSlot.groupId() == slot.groupId())
			.filter(slot -> anchorSlot.recipeKey().equals(slot.recipeKey()))
			.sorted(Comparator.comparingInt((PanelSlot<T> slot) -> slot.area().getY()).thenComparingInt(slot -> slot.area().getX()))
			.toList();
		if (recipeSlots.isEmpty()) {
			return Optional.empty();
		}

		int top = recipeSlots.stream()
			.map(PanelSlot::area)
			.mapToInt(ImmutableRect2i::getY)
			.min()
			.orElse(targetRow.area().getY());
		int bottom = recipeSlots.stream()
			.map(PanelSlot::area)
			.mapToInt(area -> area.getY() + area.getHeight())
			.max()
			.orElse(targetRow.area().getY() + targetRow.area().getHeight());
		boolean insertAfterRecipe = mouseY >= top + (bottom - top) / 2.0;
		PanelSlot<T> boundarySlot = insertAfterRecipe ? recipeSlots.get(recipeSlots.size() - 1) : recipeSlots.get(0);
		return Optional.of(new RecipeBoundaryInsertionTarget<>(
			boundarySlot.item(),
			boundarySlot.groupId(),
			boundarySlot.area(),
			insertAfterRecipe ? 1 : 0
		));
	}

	public static <T> boolean shouldUpdateDragEnd(
		RowSlot<T> start,
		@Nullable RowSlot<T> existingEnd,
		RowSlot<T> current,
		long elapsedMillis,
		long thresholdMillis
	) {
		boolean rowChanged = current.area().getY() != start.area().getY();
		boolean heldLongEnough = elapsedMillis >= thresholdMillis;
		return existingEnd != null || rowChanged || heldLongEnough;
	}

	public static <T> boolean isConnectedToPreviousRow(List<RowSlot<T>> rowSlots, int index) {
		if (index <= 0 || index >= rowSlots.size()) {
			return false;
		}
		return isConnected(rowSlots.get(index - 1), rowSlots.get(index));
	}

	public static <T> boolean isConnectedToNextRow(List<RowSlot<T>> rowSlots, int index) {
		if (index < 0 || index + 1 >= rowSlots.size()) {
			return false;
		}
		return isConnected(rowSlots.get(index), rowSlots.get(index + 1));
	}

	private static <T> boolean isConnected(RowSlot<T> first, RowSlot<T> second) {
		return !(first.groupId() == BookmarkGroupManager.DEFAULT_GROUP_ID) &&
			first.groupId() == second.groupId() &&
			first.area().getY() + first.area().getHeight() == second.area().getY();
	}

	public static <T> List<T> getItemsBetweenRecipeBounds(List<PanelSlot<T>> panelSlots, RowSlot<T> first, RowSlot<T> second) {
		Bounds bounds = getRecipeBounds(panelSlots, first, second);
		return panelSlots.stream()
			.filter(slot -> slot.area().getY() >= bounds.minY() && slot.area().getY() <= bounds.maxY())
			.map(PanelSlot::item)
			.distinct()
			.toList();
	}

	public static <T> List<RowSlot<T>> getRowsBetweenRecipeBounds(
		List<PanelSlot<T>> panelSlots,
		List<RowSlot<T>> rowSlots,
		RowSlot<T> first,
		RowSlot<T> second
	) {
		Bounds bounds = getRecipeBounds(panelSlots, first, second);
		return rowSlots.stream()
			.filter(slot -> slot.area().getY() >= bounds.minY() && slot.area().getY() <= bounds.maxY())
			.toList();
	}

	public static <T> List<RowSlot<T>> createGroupingPreviewRows(
		List<PanelSlot<T>> panelSlots,
		List<RowSlot<T>> rowSlots,
		RowSlot<T> start,
		RowSlot<T> end,
		boolean exclude,
		int previewGroupId
	) {
		List<RowSlot<T>> selectedRows = getRowsBetweenRecipeBounds(panelSlots, rowSlots, start, end);
		int targetGroupId = getGroupingPreviewTargetGroupId(start.groupId(), exclude, previewGroupId);
		List<RowSlot<T>> previewRows = rowSlots.stream()
			.map(row -> containsRowY(selectedRows, row.area().getY()) ? withGroupId(row, targetGroupId) : row)
			.toList();
		if (!exclude && !(start.groupId() == BookmarkGroupManager.DEFAULT_GROUP_ID)) {
			previewRows = releaseRowsOutsideExistingGroupPreview(previewRows, selectedRows, start.groupId(), start.area().getY(), end.area().getY());
		}
		return previewRows;
	}

	private static int getGroupingPreviewTargetGroupId(int startGroupId, boolean exclude, int previewGroupId) {
		if (exclude) {
			return BookmarkGroupManager.DEFAULT_GROUP_ID;
		}
		if ((startGroupId == BookmarkGroupManager.DEFAULT_GROUP_ID)) {
			return previewGroupId;
		}
		return startGroupId;
	}

	private static <T> List<RowSlot<T>> releaseRowsOutsideExistingGroupPreview(
		List<RowSlot<T>> rowSlots,
		List<RowSlot<T>> selectedRows,
		int groupId,
		int startY,
		int endY
	) {
		if (selectedRows.isEmpty()) {
			return rowSlots;
		}
		List<RowSlot<T>> previewRows = new ArrayList<>(rowSlots);
		int endIndex = indexOfRowY(previewRows, endY);
		if (endIndex < 0) {
			return previewRows;
		}
		int minSelectedY = selectedRows.stream().mapToInt(row -> row.area().getY()).min().orElse(endY);
		int maxSelectedY = selectedRows.stream().mapToInt(row -> row.area().getY()).max().orElse(endY);
		int direction = Integer.compare(endY, startY);
		if (direction > 0) {
			for (int i = endIndex + 1; i < previewRows.size(); i++) {
				RowSlot<T> row = previewRows.get(i);
				if (groupId != row.groupId()) {
					break;
				}
				if (row.area().getY() > maxSelectedY) {
					previewRows.set(i, withGroupId(row, BookmarkGroupManager.DEFAULT_GROUP_ID));
				}
			}
		} else if (direction < 0) {
			for (int i = endIndex - 1; i >= 0; i--) {
				RowSlot<T> row = previewRows.get(i);
				if (groupId != row.groupId()) {
					break;
				}
				if (row.area().getY() < minSelectedY) {
					previewRows.set(i, withGroupId(row, BookmarkGroupManager.DEFAULT_GROUP_ID));
				}
			}
		}
		return previewRows;
	}

	private static <T> boolean containsRowY(List<RowSlot<T>> rows, int y) {
		return rows.stream()
			.anyMatch(row -> row.area().getY() == y);
	}

	private static <T> int indexOfRowY(List<RowSlot<T>> rows, int y) {
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).area().getY() == y) {
				return i;
			}
		}
		return -1;
	}

	private static <T> RowSlot<T> withGroupId(RowSlot<T> row, int groupId) {
		return new RowSlot<>(row.item(), groupId, row.area());
	}

	private static <T> Bounds getRecipeBounds(List<PanelSlot<T>> panelSlots, RowSlot<T> first, RowSlot<T> second) {
		int firstTop = getTopRecipeBoundary(panelSlots, first.area().getY());
		int firstBottom = getBottomRecipeBoundary(panelSlots, first.area().getY());
		int secondTop = getTopRecipeBoundary(panelSlots, second.area().getY());
		int secondBottom = getBottomRecipeBoundary(panelSlots, second.area().getY());
		return new Bounds(
			Math.min(firstTop, secondTop),
			Math.max(firstBottom, secondBottom)
		);
	}

	private static <T> int getTopRecipeBoundary(List<PanelSlot<T>> panelSlots, int rowY) {
		return getRecipeBoundary(panelSlots, rowY, true);
	}

	private static <T> int getBottomRecipeBoundary(List<PanelSlot<T>> panelSlots, int rowY) {
		return getRecipeBoundary(panelSlots, rowY, false);
	}

	private static <T> int getRecipeBoundary(List<PanelSlot<T>> panelSlots, int rowY, boolean firstSlot) {
		Optional<PanelSlot<T>> boundarySlot = panelSlots.stream()
			.filter(slot -> slot.area().getY() == rowY)
			.min(firstSlot ?
				Comparator.comparingInt(slot -> slot.area().getX()) :
				Comparator.comparingInt((PanelSlot<T> slot) -> slot.area().getX()).reversed());
		if (boundarySlot.isEmpty() || boundarySlot.get().recipeKey() == null) {
			return rowY;
		}
		PanelSlot<T> anchor = boundarySlot.get();
		return panelSlots.stream()
			.filter(slot -> anchor.groupId() == slot.groupId())
			.filter(slot -> anchor.recipeKey().equals(slot.recipeKey()))
			.map(PanelSlot::area)
			.mapToInt(ImmutableRect2i::getY)
			.reduce(rowY, firstSlot ? Math::min : Math::max);
	}

	private record Bounds(int minY, int maxY) {
	}
}

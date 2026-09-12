package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.IBookmark;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record BookmarkItemMovePlan(
	IBookmark targetBookmark,
	int targetGroupId,
	int offset,
	boolean rejected
) {
	public BookmarkItemMovePlan(IBookmark targetBookmark, int targetGroupId, int offset) {
		this(targetBookmark, targetGroupId, offset, false);
	}

	public static BookmarkItemMovePlan createSameGroupByRow(
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		IBookmark sourceBookmark,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow,
		List<IBookmark> orderedBookmarks
	) {
		return new BookmarkItemMovePlan(targetRow.item(), targetRow.groupId(), getSameGroupOffset(rowSlots, sourceBookmark, targetRow, orderedBookmarks));
	}

	public static BookmarkItemMovePlan createSameGroupByRow(
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		IBookmark sourceBookmark,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow,
		List<IBookmark> orderedBookmarks,
		double mouseY
	) {
		Optional<BookmarkPanelLayout.RecipeBoundaryInsertionTarget<IBookmark>> recipeBoundaryTarget = BookmarkPanelLayout.getRecipeBoundaryInsertionTarget(panelSlots, targetRow, mouseY);
		if (recipeBoundaryTarget.isPresent()) {
			BookmarkPanelLayout.RecipeBoundaryInsertionTarget<IBookmark> target = recipeBoundaryTarget.get();
			return new BookmarkItemMovePlan(target.item(), target.groupId(), target.offset());
		}
		return createSameGroupByRow(rowSlots, sourceBookmark, targetRow, orderedBookmarks);
	}

	public static BookmarkItemMovePlan createCrossGroup(
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow,
		double mouseY,
		Map<Integer, BookmarkGroup> groups,
		Set<ResourceLocation> movingRecipeIds,
		Map<Integer, Set<ResourceLocation>> recipeIdsByGroup
	) {
		return createCrossGroupPlan(rowSlots, targetRow, mouseY, groups, movingRecipeIds, recipeIdsByGroup);
	}

	public static BookmarkItemMovePlan createCrossGroup(
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow,
		double mouseY,
		Map<Integer, BookmarkGroup> groups,
		Set<ResourceLocation> movingRecipeIds,
		Map<Integer, Set<ResourceLocation>> recipeIdsByGroup
	) {
		BookmarkItemMovePlan plan = createCrossGroupPlan(rowSlots, targetRow, mouseY, groups, movingRecipeIds, recipeIdsByGroup);
		if (plan.rejected()) {
			return plan;
		}
		Optional<BookmarkPanelLayout.RecipeBoundaryInsertionTarget<IBookmark>> recipeBoundaryTarget = BookmarkPanelLayout.getRecipeBoundaryInsertionTarget(panelSlots, targetRow, mouseY);
		if (recipeBoundaryTarget.isPresent()) {
			BookmarkPanelLayout.RecipeBoundaryInsertionTarget<IBookmark> target = recipeBoundaryTarget.get();
			if (plan.targetGroupId() == target.groupId()) {
				return createPlan(target.item(), plan.targetGroupId(), target.offset(), movingRecipeIds, recipeIdsByGroup);
			}
		}
		return plan;
	}

	private static BookmarkItemMovePlan createCrossGroupPlan(
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow,
		double mouseY,
		Map<Integer, BookmarkGroup> groups,
		Set<ResourceLocation> movingRecipeIds,
		Map<Integer, Set<ResourceLocation>> recipeIdsByGroup
	) {
		double ySlot = (mouseY - targetRow.area().getY()) / targetRow.area().getHeight();
		int rowIndex = findRowIndex(rowSlots, targetRow);
		Optional<BookmarkPanelLayout.RowSlot<IBookmark>> beforeRow = rowIndex > 0 ? Optional.of(rowSlots.get(rowIndex - 1)) : Optional.empty();
		Optional<BookmarkPanelLayout.RowSlot<IBookmark>> afterRow = rowIndex >= 0 && rowIndex + 1 < rowSlots.size() ? Optional.of(rowSlots.get(rowIndex + 1)) : Optional.empty();

		Optional<BookmarkItemMovePlan> plan;
		if (ySlot <= 0.25) {
			plan = createNeighborPlan(beforeRow, targetRow, groups, 0, movingRecipeIds, recipeIdsByGroup);
		} else if (ySlot <= 0.5) {
			plan = createMiddlePlan(beforeRow, targetRow, afterRow, groups, 0, movingRecipeIds, recipeIdsByGroup);
		} else if (ySlot < 0.75) {
			plan = createMiddlePlan(afterRow, targetRow, beforeRow, groups, 1, movingRecipeIds, recipeIdsByGroup);
		} else {
			plan = createNeighborPlan(afterRow, targetRow, groups, 1, movingRecipeIds, recipeIdsByGroup);
		}

		return plan.isPresent() ? plan.get() : reject(targetRow);
	}

	private static Optional<BookmarkItemMovePlan> createNeighborPlan(
		Optional<BookmarkPanelLayout.RowSlot<IBookmark>> neighborRow,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow,
		Map<Integer, BookmarkGroup> groups,
		int offset,
		Set<ResourceLocation> movingRecipeIds,
		Map<Integer, Set<ResourceLocation>> recipeIdsByGroup
	) {
		return neighborRow
			.filter(row -> row.groupId() != targetRow.groupId())
			.filter(row -> canInsert(groups, row.groupId()))
			.map(row -> createPlan(targetRow.item(), row.groupId(), offset, movingRecipeIds, recipeIdsByGroup));
	}

	private static Optional<BookmarkItemMovePlan> createMiddlePlan(
		Optional<BookmarkPanelLayout.RowSlot<IBookmark>> preferredRow,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow,
		Optional<BookmarkPanelLayout.RowSlot<IBookmark>> oppositeRow,
		Map<Integer, BookmarkGroup> groups,
		int offset,
		Set<ResourceLocation> movingRecipeIds,
		Map<Integer, Set<ResourceLocation>> recipeIdsByGroup
	) {
		if (preferredRow.isEmpty()) {
			return Optional.empty();
		}
		int preferredGroupId = preferredRow.get().groupId();
		if (preferredGroupId == targetRow.groupId()) {
			return Optional.empty();
		}

		int targetGroupId = preferredGroupId;
		if (!(targetRow.groupId() == BookmarkGroupManager.DEFAULT_GROUP_ID) &&
			!(preferredGroupId == BookmarkGroupManager.DEFAULT_GROUP_ID)
		) {
			targetGroupId = BookmarkGroupManager.DEFAULT_GROUP_ID;
		}

		if ((targetRow.groupId() == BookmarkGroupManager.DEFAULT_GROUP_ID) &&
			!(preferredGroupId == BookmarkGroupManager.DEFAULT_GROUP_ID) &&
			oppositeRow.filter(row -> !(row.groupId() == BookmarkGroupManager.DEFAULT_GROUP_ID)).isPresent()
		) {
			return Optional.empty();
		}

		if (!canInsert(groups, targetGroupId)) {
			return Optional.empty();
		}
		return Optional.of(createPlan(targetRow.item(), targetGroupId, offset, movingRecipeIds, recipeIdsByGroup));
	}

	private static int findRowIndex(
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow
	) {
		for (int i = 0; i < rowSlots.size(); i++) {
			BookmarkPanelLayout.RowSlot<IBookmark> row = rowSlots.get(i);
			if (row.area().getY() == targetRow.area().getY()) {
				return i;
			}
		}
		return -1;
	}

	private static int getSameGroupOffset(
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		IBookmark sourceBookmark,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow,
		List<IBookmark> orderedBookmarks
	) {
		Optional<Integer> rowOffset = rowSlots.stream()
			.filter(row -> row.item().equals(sourceBookmark))
			.findFirst()
			.map(sourceRow -> sourceRow.area().getY() < targetRow.area().getY() ? 1 : 0);
		if (rowOffset.isPresent()) {
			return rowOffset.get();
		}
		int sourceIndex = orderedBookmarks.indexOf(sourceBookmark);
		int targetIndex = orderedBookmarks.indexOf(targetRow.item());
		if (sourceIndex >= 0 && targetIndex >= 0 && sourceIndex > targetIndex) {
			return 1;
		}
		return 0;
	}

	private static boolean canInsert(Map<Integer, BookmarkGroup> groups, int groupId) {
		if ((groupId == BookmarkGroupManager.DEFAULT_GROUP_ID)) {
			return true;
		}
		return Optional.ofNullable(groups.get(groupId))
			.map(group -> !group.collapsed() && group.viewMode() == BookmarkViewMode.TODO_LIST)
			.orElse(false);
	}

	private static BookmarkItemMovePlan createPlan(
		IBookmark targetBookmark,
		int targetGroupId,
		int offset,
		Set<ResourceLocation> movingRecipeIds,
		Map<Integer, Set<ResourceLocation>> recipeIdsByGroup
	) {
		boolean duplicateRecipe = movingRecipeIds.stream()
			.anyMatch(recipeIdsByGroup.getOrDefault(targetGroupId, Set.of())::contains);
		return new BookmarkItemMovePlan(targetBookmark, targetGroupId, offset, duplicateRecipe);
	}

	private static BookmarkItemMovePlan reject(BookmarkPanelLayout.RowSlot<IBookmark> targetRow) {
		return new BookmarkItemMovePlan(targetRow.item(), targetRow.groupId(), 0, true);
	}
}

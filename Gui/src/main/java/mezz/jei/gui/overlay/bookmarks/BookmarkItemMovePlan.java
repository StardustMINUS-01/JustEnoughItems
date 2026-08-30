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
	String targetGroupId,
	int offset,
	boolean rejected
) {
	public BookmarkItemMovePlan(IBookmark targetBookmark, String targetGroupId, int offset) {
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
		Optional<BookmarkPanelLayout.RecipeBoundaryInsertionTarget<IBookmark>> recipeBoundaryTarget =
			BookmarkPanelLayout.getRecipeBoundaryInsertionTarget(panelSlots, targetRow, mouseY);
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
		Map<String, BookmarkGroup> groups,
		Set<ResourceLocation> movingRecipeIds,
		Map<String, Set<ResourceLocation>> recipeIdsByGroup
	) {
		return createCrossGroupPlan(rowSlots, targetRow, mouseY, groups, movingRecipeIds, recipeIdsByGroup);
	}

	public static BookmarkItemMovePlan createCrossGroup(
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow,
		double mouseY,
		Map<String, BookmarkGroup> groups,
		Set<ResourceLocation> movingRecipeIds,
		Map<String, Set<ResourceLocation>> recipeIdsByGroup
	) {
		BookmarkItemMovePlan plan = createCrossGroupPlan(rowSlots, targetRow, mouseY, groups, movingRecipeIds, recipeIdsByGroup);
		if (plan.rejected()) {
			return plan;
		}
		Optional<BookmarkPanelLayout.RecipeBoundaryInsertionTarget<IBookmark>> recipeBoundaryTarget =
			BookmarkPanelLayout.getRecipeBoundaryInsertionTarget(panelSlots, targetRow, mouseY);
		if (recipeBoundaryTarget.isPresent()) {
			BookmarkPanelLayout.RecipeBoundaryInsertionTarget<IBookmark> target = recipeBoundaryTarget.get();
			if (plan.targetGroupId().equals(target.groupId())) {
				return createPlan(target.item(), plan.targetGroupId(), target.offset(), movingRecipeIds, recipeIdsByGroup);
			}
		}
		return plan;
	}

	private static BookmarkItemMovePlan createCrossGroupPlan(
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow,
		double mouseY,
		Map<String, BookmarkGroup> groups,
		Set<ResourceLocation> movingRecipeIds,
		Map<String, Set<ResourceLocation>> recipeIdsByGroup
	) {
		double ySlot = (mouseY - targetRow.area().getY()) / targetRow.area().getHeight();
		Optional<BookmarkPanelLayout.RowSlot<IBookmark>> beforeRow = findAdjacentRow(rowSlots, targetRow, -1);
		Optional<BookmarkPanelLayout.RowSlot<IBookmark>> afterRow = findAdjacentRow(rowSlots, targetRow, 1);

		if (ySlot <= 0.25) {
			Optional<BookmarkItemMovePlan> plan = createNeighborPlan(beforeRow, targetRow, groups, 0, movingRecipeIds, recipeIdsByGroup);
			if (plan.isPresent()) {
				return plan.get();
			}
		} else if (ySlot <= 0.5) {
			Optional<BookmarkItemMovePlan> plan = createMiddlePlan(beforeRow, targetRow, afterRow, groups, 0, movingRecipeIds, recipeIdsByGroup);
			if (plan.isPresent()) {
				return plan.get();
			}
		} else if (ySlot < 0.75) {
			Optional<BookmarkItemMovePlan> plan = createMiddlePlan(afterRow, targetRow, beforeRow, groups, 1, movingRecipeIds, recipeIdsByGroup);
			if (plan.isPresent()) {
				return plan.get();
			}
		} else {
			Optional<BookmarkItemMovePlan> plan = createNeighborPlan(afterRow, targetRow, groups, 1, movingRecipeIds, recipeIdsByGroup);
			if (plan.isPresent()) {
				return plan.get();
			}
		}

		return reject(targetRow);
	}

	private static Optional<BookmarkItemMovePlan> createNeighborPlan(
		Optional<BookmarkPanelLayout.RowSlot<IBookmark>> neighborRow,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow,
		Map<String, BookmarkGroup> groups,
		int offset,
		Set<ResourceLocation> movingRecipeIds,
		Map<String, Set<ResourceLocation>> recipeIdsByGroup
	) {
		return neighborRow
			.filter(row -> !row.groupId().equals(targetRow.groupId()))
			.filter(row -> canInsert(groups, row.groupId()))
			.map(row -> createPlan(targetRow.item(), row.groupId(), offset, movingRecipeIds, recipeIdsByGroup));
	}

	private static Optional<BookmarkItemMovePlan> createMiddlePlan(
		Optional<BookmarkPanelLayout.RowSlot<IBookmark>> preferredRow,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow,
		Optional<BookmarkPanelLayout.RowSlot<IBookmark>> oppositeRow,
		Map<String, BookmarkGroup> groups,
		int offset,
		Set<ResourceLocation> movingRecipeIds,
		Map<String, Set<ResourceLocation>> recipeIdsByGroup
	) {
		if (preferredRow.isEmpty()) {
			return Optional.empty();
		}
		String preferredGroupId = preferredRow.get().groupId();
		if (preferredGroupId.equals(targetRow.groupId())) {
			return Optional.empty();
		}

		String targetGroupId = preferredGroupId;
		if (!BookmarkGroupManager.DEFAULT_GROUP_ID.equals(targetRow.groupId()) &&
			!BookmarkGroupManager.DEFAULT_GROUP_ID.equals(preferredGroupId)) {
			targetGroupId = BookmarkGroupManager.DEFAULT_GROUP_ID;
		}

		if (BookmarkGroupManager.DEFAULT_GROUP_ID.equals(targetRow.groupId()) &&
			!BookmarkGroupManager.DEFAULT_GROUP_ID.equals(preferredGroupId) &&
			oppositeRow.filter(row -> !BookmarkGroupManager.DEFAULT_GROUP_ID.equals(row.groupId())).isPresent()) {
			return Optional.empty();
		}

		if (!canInsert(groups, targetGroupId)) {
			return Optional.empty();
		}
		return Optional.of(createPlan(targetRow.item(), targetGroupId, offset, movingRecipeIds, recipeIdsByGroup));
	}

	private static Optional<BookmarkPanelLayout.RowSlot<IBookmark>> findAdjacentRow(
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		BookmarkPanelLayout.RowSlot<IBookmark> targetRow,
		int offset
	) {
		for (int i = 0; i < rowSlots.size(); i++) {
			BookmarkPanelLayout.RowSlot<IBookmark> row = rowSlots.get(i);
			if (row.area().getY() == targetRow.area().getY()) {
				int index = i + offset;
				if (index >= 0 && index < rowSlots.size()) {
					return Optional.of(rowSlots.get(index));
				}
				return Optional.empty();
			}
		}
		return Optional.empty();
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

	private static boolean canInsert(Map<String, BookmarkGroup> groups, String groupId) {
		if (BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId)) {
			return true;
		}
		return Optional.ofNullable(groups.get(groupId))
			.map(group -> !group.collapsed() && group.viewMode() == BookmarkViewMode.TODO_LIST)
			.orElse(false);
	}

	private static BookmarkItemMovePlan createPlan(
		IBookmark targetBookmark,
		String targetGroupId,
		int offset,
		Set<ResourceLocation> movingRecipeIds,
		Map<String, Set<ResourceLocation>> recipeIdsByGroup
	) {
		boolean duplicateRecipe = movingRecipeIds.stream()
			.anyMatch(recipeIdsByGroup.getOrDefault(targetGroupId, Set.of())::contains);
		return new BookmarkItemMovePlan(targetBookmark, targetGroupId, offset, duplicateRecipe);
	}

	private static BookmarkItemMovePlan reject(BookmarkPanelLayout.RowSlot<IBookmark> targetRow) {
		return new BookmarkItemMovePlan(targetRow.item(), targetRow.groupId(), 0, true);
	}
}

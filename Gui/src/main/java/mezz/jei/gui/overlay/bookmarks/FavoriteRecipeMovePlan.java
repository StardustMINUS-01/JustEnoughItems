package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record FavoriteRecipeMovePlan(
	FocusedRecipe targetRecipe,
	int offset,
	List<ImmutableRect2i> targetAreas
) {
	public FavoriteRecipeMovePlan {
		targetAreas = List.copyOf(targetAreas);
	}

	public static Optional<FavoriteRecipeMovePlan> create(
		List<BookmarkPanelLayout.PanelSlot<FocusedRecipe>> panelSlots,
		FocusedRecipe sourceRecipe,
		double mouseY
	) {
		List<RecipeBlock> blocks = createRecipeBlocks(panelSlots);
		if (blocks.isEmpty()) {
			return Optional.empty();
		}

		for (RecipeBlock block : blocks) {
			if (block.containsY(mouseY)) {
				return createPlan(sourceRecipe, block, mouseY < block.middleY() ? 0 : 1);
			}
		}
		for (RecipeBlock block : blocks) {
			if (mouseY < block.middleY()) {
				return createPlan(sourceRecipe, block, 0);
			}
		}
		return createPlan(sourceRecipe, blocks.get(blocks.size() - 1), 1);
	}

	private static Optional<FavoriteRecipeMovePlan> createPlan(FocusedRecipe sourceRecipe, RecipeBlock targetBlock, int offset) {
		if (sourceRecipe.equals(targetBlock.recipe())) {
			return Optional.empty();
		}
		return Optional.of(new FavoriteRecipeMovePlan(targetBlock.recipe(), offset, targetBlock.areas()));
	}

	private static List<RecipeBlock> createRecipeBlocks(List<BookmarkPanelLayout.PanelSlot<FocusedRecipe>> panelSlots) {
		List<BookmarkPanelLayout.PanelSlot<FocusedRecipe>> sortedSlots = panelSlots.stream()
			.sorted(Comparator.comparingInt((BookmarkPanelLayout.PanelSlot<FocusedRecipe> slot) -> slot.area().getY())
				.thenComparingInt(slot -> slot.area().getX()))
			.toList();
		List<RecipeBlock> blocks = new ArrayList<>();
		for (BookmarkPanelLayout.PanelSlot<FocusedRecipe> slot : sortedSlots) {
			if (blocks.stream().anyMatch(block -> block.recipe().equals(slot.item()))) {
				continue;
			}
			List<ImmutableRect2i> areas = sortedSlots.stream()
				.filter(candidate -> slot.item().equals(candidate.item()))
				.map(BookmarkPanelLayout.PanelSlot::area)
				.toList();
			blocks.add(new RecipeBlock(slot.item(), areas));
		}
		return List.copyOf(blocks);
	}

	private record RecipeBlock(FocusedRecipe recipe, List<ImmutableRect2i> areas) {
		private boolean containsY(double mouseY) {
			return mouseY >= top() && mouseY < bottom();
		}

		private double middleY() {
			int top = top();
			int bottom = bottom();
			return top + (bottom - top) / 2.0;
		}

		private int top() {
			return areas.stream()
				.mapToInt(ImmutableRect2i::getY)
				.min()
				.orElse(0);
		}

		private int bottom() {
			int top = areas.stream()
				.mapToInt(ImmutableRect2i::getY)
				.min()
				.orElse(0);
			return areas.stream()
				.mapToInt(area -> area.getY() + area.getHeight())
				.max()
				.orElse(top);
		}
	}
}

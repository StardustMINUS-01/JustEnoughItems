package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

public record FavoriteRecipeMovePlan(FocusedRecipe targetRecipe, int offset) {
	public static Optional<FavoriteRecipeMovePlan> create(
		List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> panelSlots,
		FocusedRecipe sourceRecipe,
		double mouseY
	) {
		var boundsByRecipe = new LinkedHashMap<FocusedRecipe, RecipeBounds>();
		for (var slot : panelSlots.stream().sorted(BookmarkPanelLayout.SLOT_ORDER).toList()) {
			var area = slot.area();
			var bounds = boundsByRecipe.computeIfAbsent(slot.item().getFocusedRecipe(), recipe -> new RecipeBounds(area.getY()));
			bounds.bottom = Math.max(bounds.bottom, area.getY() + area.getHeight());
		}
		for (var entry : boundsByRecipe.entrySet()) {
			var bounds = entry.getValue();
			if (mouseY < bounds.bottom) {
				double middle = bounds.top + (bounds.bottom - bounds.top) / 2.0;
				return createPlan(sourceRecipe, entry.getKey(), mouseY < middle ? 0 : 1);
			}
		}
		return boundsByRecipe.isEmpty() ? Optional.empty() : createPlan(sourceRecipe, boundsByRecipe.lastEntry().getKey(), 1);
	}

	private static Optional<FavoriteRecipeMovePlan> createPlan(FocusedRecipe source, FocusedRecipe target, int offset) {
		return source.equals(target) ? Optional.empty() : Optional.of(new FavoriteRecipeMovePlan(target, offset));
	}

	private static final class RecipeBounds {
		final int top;
		int bottom;

		RecipeBounds(int top) {
			this.top = top;
			this.bottom = top;
		}
	}
}

package mezz.jei.gui.bookmarks;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.Internal;
import mezz.jei.gui.recipes.InteractiveIngredientGridTooltipComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BookmarkCandidateTooltipState {
	private List<BookmarkIngredientKey> candidates = List.of();
	private Optional<InteractiveIngredientGridTooltipComponent> grid = Optional.empty();

	public Optional<InteractiveIngredientGridTooltipComponent> getOrCreate(List<BookmarkIngredientKey> keys) {
		if (!candidates.equals(keys)) {
			candidates = List.copyOf(keys);
			List<ITypedIngredient<?>> ingredients = new ArrayList<>();
			for (BookmarkIngredientKey key : keys) {
				ITypedIngredient<?> typed = key.typedIngredient();
				if (typed != null) {
					ingredients.add(typed);
				}
			}
			grid = ingredients.size() > 1
				? Optional.of(new InteractiveIngredientGridTooltipComponent(Internal.getJeiRuntime().getRecipeManager(), ingredients))
				: Optional.empty();
		}
		return grid;
	}
}

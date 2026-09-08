package mezz.jei.gui.bookmarks;

import mezz.jei.common.Internal;
import mezz.jei.gui.recipes.InteractiveIngredientGridTooltipComponent;

import java.util.List;
import java.util.Optional;

public final class BookmarkCandidateTooltipState {
	private List<BookmarkIngredientKey> candidates = List.of();
	private Optional<InteractiveIngredientGridTooltipComponent> grid = Optional.empty();

	public Optional<InteractiveIngredientGridTooltipComponent> getOrCreate(List<BookmarkIngredientKey> keys) {
		if (!candidates.equals(keys)) {
			candidates = List.copyOf(keys);
			var ingredients = keys.stream().map(BookmarkIngredientKey::typedIngredient).filter(java.util.Objects::nonNull).toList();
			grid = ingredients.size() > 1 ? Optional.of(new InteractiveIngredientGridTooltipComponent(
				Internal.getJeiRuntime().getRecipeManager(), ingredients)) : Optional.empty();
		}
		return grid;
	}
}

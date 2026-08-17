package mezz.jei.gui.bookmarks;

import mezz.jei.api.gui.IRecipeLayoutDrawable;

import java.util.Map;
import java.util.Optional;

/**
 * A recipe layout plus the selected output/input keys, ported from JEI 1.21.1
 * ({@code mezz.jei.gui.bookmarks.RecipeLayoutProjection}).
 */
public record RecipeLayoutProjection(
	IRecipeLayoutDrawable<?> layout,
	Optional<BookmarkIngredientKey> selectedOutputKey,
	Map<Integer, BookmarkIngredientKey> selectedInputKeys
) {
	public RecipeLayoutProjection {
		selectedOutputKey = selectedOutputKey == null ? Optional.empty() : selectedOutputKey;
		selectedInputKeys = selectedInputKeys == null ? Map.of() : Map.copyOf(selectedInputKeys);
	}

	public RecipeLayoutProjection(IRecipeLayoutDrawable<?> layout) {
		this(layout, Optional.empty(), Map.of());
	}

	public RecipeLayoutProjection(IRecipeLayoutDrawable<?> layout, Map<Integer, BookmarkIngredientKey> selectedInputKeys) {
		this(layout, Optional.empty(), selectedInputKeys);
	}

	public Optional<BookmarkIngredientKey> selectedInputKey(int inputSlotIndex) {
		return Optional.ofNullable(selectedInputKeys.get(inputSlotIndex));
	}
}

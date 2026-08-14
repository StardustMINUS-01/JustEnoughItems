package mezz.jei.gui.favorites;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;

import java.util.Objects;

public record SlotVariant(
	BookmarkIngredientKey key,
	ITypedIngredient<?> ingredient
) {
	public SlotVariant {
		Objects.requireNonNull(key);
		Objects.requireNonNull(ingredient);
	}
}

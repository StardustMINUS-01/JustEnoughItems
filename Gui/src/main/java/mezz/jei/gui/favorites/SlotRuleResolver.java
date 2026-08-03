package mezz.jei.gui.favorites;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
public interface SlotRuleResolver {
	Optional<FocusedRecipe> resolveSlot(List<BookmarkIngredientKey> variants);
}

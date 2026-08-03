package mezz.jei.gui.favorites;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.List;
import java.util.Optional;

/**
 * Resolves a preferred recipe for a multi-variant input slot, used by the favorite
 * recipe tree. Returns a recipe only when the slot-level preference collapses to
 * exactly one recipe; otherwise the tree falls back to the displayed variant.
 */
@FunctionalInterface
public interface SlotRuleResolver {
	Optional<FocusedRecipe> resolveSlot(List<BookmarkIngredientKey> variants);
}

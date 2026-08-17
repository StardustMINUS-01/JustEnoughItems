package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import org.jetbrains.annotations.Nullable;

record RecipeChainRemainderStack(
	BookmarkIngredientKey key,
	long remainingUses,
	@Nullable BookmarkIngredientKey brokenKey
) {
	RecipeChainRemainderStack withRemainingUses(long remainingUses) {
		return new RecipeChainRemainderStack(key, remainingUses, brokenKey);
	}
}

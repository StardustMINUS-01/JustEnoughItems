package mezz.jei.gui.favorites;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;

import java.util.Objects;
import java.util.Set;

public record RecipeCandidateResult(
	RecipePreferenceCandidate candidate,
	Set<BookmarkIngredientKey> outputKeys
) {
	public RecipeCandidateResult {
		candidate = Objects.requireNonNull(candidate);
		outputKeys = outputKeys == null ? Set.of() : Set.copyOf(outputKeys);
	}
}

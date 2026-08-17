package mezz.jei.gui.favorites;

import java.util.Optional;

/**
 * Internal factory that materializes the full candidate data for a recipe.
 */
public interface IRecipeCandidateFactory {
	Optional<RecipeCandidateResult> create(RecipeCandidateReference reference);

	default Optional<RecipeCandidateResult> create(
		RecipeCandidateReference reference,
		RecipeLayoutBuildCache layoutCache
	) {
		return create(reference);
	}
}

package mezz.jei.gui.favorites;

import mezz.jei.gui.input.FocusedRecipe;

import java.util.Objects;

public record RecipeCandidateReference(
	FocusedRecipe focusedRecipe,
	Object recipe
) {
	public RecipeCandidateReference {
		Objects.requireNonNull(focusedRecipe);
		Objects.requireNonNull(recipe);
	}
}

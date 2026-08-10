package mezz.jei.gui.favorites.preferences;

import mezz.jei.gui.input.FocusedRecipe;

import java.util.List;

public record RecipePreferenceCandidate(
	FocusedRecipe recipe,
	List<RecipePreferenceIngredientInfo> inputs,
	List<RecipePreferenceIngredientInfo> outputs
) {
	public RecipePreferenceCandidate {
		inputs = inputs == null ? List.of() : List.copyOf(inputs);
		outputs = outputs == null ? List.of() : List.copyOf(outputs);
	}

	public RecipePreferenceCandidate(FocusedRecipe recipe, List<RecipePreferenceIngredientInfo> inputs) {
		this(recipe, inputs, List.of());
	}
}

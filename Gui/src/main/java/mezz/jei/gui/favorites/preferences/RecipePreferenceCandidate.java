package mezz.jei.gui.favorites.preferences;

import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.match.IngredientMatchInfo;

import java.util.List;

public record RecipePreferenceCandidate(
	FocusedRecipe recipe,
	List<IngredientMatchInfo> inputs,
	List<IngredientMatchInfo> outputs
) {
	public RecipePreferenceCandidate {
		inputs = inputs == null ? List.of() : List.copyOf(inputs);
		outputs = outputs == null ? List.of() : List.copyOf(outputs);
	}

	public RecipePreferenceCandidate(FocusedRecipe recipe, List<IngredientMatchInfo> inputs) {
		this(recipe, inputs, List.of());
	}
}

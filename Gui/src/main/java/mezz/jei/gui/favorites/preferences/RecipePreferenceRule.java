package mezz.jei.gui.favorites.preferences;

import mezz.jei.gui.match.IngredientExpression;

import java.util.Optional;

public record RecipePreferenceRule(
	IngredientExpression output,
	Optional<IngredientExpression> input,
	Optional<IngredientExpression> recipe
) {
	public RecipePreferenceRule {
		input = input == null ? Optional.empty() : input;
		recipe = recipe == null ? Optional.empty() : recipe;
	}
}

package mezz.jei.gui.favorites.preferences;

import java.util.Optional;

public record RecipePreferenceRule(
	String name,
	RecipePreferenceExpression output,
	Optional<RecipePreferenceExpression> input,
	Optional<RecipePreferenceExpression> recipe
) {
	public RecipePreferenceRule {
		name = name == null || name.isBlank() ? "unnamed" : name;
		input = input == null ? Optional.empty() : input;
		recipe = recipe == null ? Optional.empty() : recipe;
	}
}

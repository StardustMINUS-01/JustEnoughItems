package mezz.jei.gui.recipes.filtering;

import java.util.List;
import java.util.Locale;

public record RecipeSearchDocument(
	List<RecipeSearchIngredient> inputs,
	List<RecipeSearchIngredient> outputs,
	List<RecipeSearchIngredient> catalysts,
	List<String> recipeText
) {
	public RecipeSearchDocument {
		inputs = List.copyOf(inputs);
		outputs = List.copyOf(outputs);
		catalysts = List.copyOf(catalysts);
		recipeText = recipeText.stream()
			.map(value -> value.toLowerCase(Locale.ROOT))
			.toList();
	}
}

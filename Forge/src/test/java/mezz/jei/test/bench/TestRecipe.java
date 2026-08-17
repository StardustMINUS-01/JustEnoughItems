package mezz.jei.test.bench;

import mezz.jei.test.lib.TestIngredient;

import java.util.List;

/**
 * A synthetic recipe for the 1.20.1 recipe-tree benchmark: a stable uid plus
 * the list of ingredients the recipe consumes (its INPUT role). The recipe
 * index ({@link RecipeMap}) is built from these inputs, so a recipe "consumes"
 * an ingredient exactly when the ingredient appears in {@code inputs}.
 */
public record TestRecipe(String uid, List<TestIngredient> inputs) {
	public TestRecipe {
		inputs = List.copyOf(inputs);
	}
}

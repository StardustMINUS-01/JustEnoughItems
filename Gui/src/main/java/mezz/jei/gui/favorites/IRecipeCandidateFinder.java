package mezz.jei.gui.favorites;

import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.List;

/**
 * Internal lookup for recipes that may produce an output ingredient.
 * Results are not verified and may include catalyst fallback noise.
 */
public interface IRecipeCandidateFinder {
	List<RecipeCandidateReference> findRecipes(ITypedIngredient<?> output);
}

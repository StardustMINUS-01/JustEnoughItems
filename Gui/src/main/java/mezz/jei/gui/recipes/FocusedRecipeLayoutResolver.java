package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.recipes.RecipeIdentifierLookup;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.Optional;

public final class FocusedRecipeLayoutResolver {
	private final IRecipeManager recipeManager;

	public FocusedRecipeLayoutResolver(IRecipeManager recipeManager) {
		this.recipeManager = recipeManager;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public Optional<IRecipeLayoutDrawable<?>> resolve(FocusedRecipe focusedRecipe, IFocusGroup focuses) {
		Optional<RecipeType<?>> recipeType = recipeManager.getRecipeType(focusedRecipe.recipeTypeUid());
		if (recipeType.isEmpty()) {
			return Optional.empty();
		}
		IRecipeCategory recipeCategory = recipeManager.getRecipeCategory((RecipeType) recipeType.get());
		var recipeLookup = recipeManager.createRecipeLookup(recipeCategory.getRecipeType())
			.limitFocus(focuses.getAllFocuses());
		return RecipeIdentifierLookup.findRecipe(recipeCategory, recipeLookup, focusedRecipe.recipeUid(), recipeManager)
			.flatMap(candidate -> recipeManager.createRecipeLayoutDrawable(recipeCategory, candidate, focuses))
			.map(layout -> (IRecipeLayoutDrawable<?>) layout);
	}
}

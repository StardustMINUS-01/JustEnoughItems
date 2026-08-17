package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves a {@link FocusedRecipe} (recipe type uid + recipe uid) into a
 * {@link IRecipeLayoutDrawable}, ported from JEI 1.21.1
 * ({@code mezz.jei.gui.recipes.FocusedRecipeLayoutResolver}).
 */
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
		return recipeManager.createRecipeLookup(recipeCategory.getRecipeType())
			.limitFocus(focuses.getAllFocuses())
			.get()
			.filter(candidate -> Objects.equals(recipeCategory.getRegistryName(candidate), focusedRecipe.recipeUid()))
			.findFirst()
			.flatMap(candidate -> recipeManager.createRecipeLayoutDrawable(recipeCategory, candidate, focuses))
			.map(layout -> (IRecipeLayoutDrawable<?>) layout);
	}
}

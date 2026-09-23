package mezz.jei.library.util;

import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.library.focus.FocusGroup;
import mezz.jei.library.gui.recipes.supplier.builder.IngredientSupplierBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class IngredientSupplierHelper {
	private static final Logger LOGGER = LogManager.getLogger();

	private IngredientSupplierHelper() {
	}

	public static <T> IIngredientSupplier getIngredientSupplier(T recipe, IRecipeCategory<T> recipeCategory, IIngredientManager ingredientManager) {
		return extractIngredients(recipe, recipeCategory, ingredientManager).supplier();
	}

	public static <T> Extraction extractIngredients(T recipe, IRecipeCategory<T> recipeCategory, IIngredientManager ingredientManager) {
		IngredientSupplierBuilder builder = new IngredientSupplierBuilder(ingredientManager);
		if (!recipeCategory.isHandled(recipe)) {
			return new Extraction(builder.buildIngredientSupplier(), false);
		}
		try {
			recipeCategory.setRecipe(builder, recipe, FocusGroup.EMPTY);
		} catch (RuntimeException | LinkageError e) {
			String recipeInfo = ErrorUtil.getRecipeInfo(recipeCategory, recipe);
			LOGGER.error("Found a broken recipe, failed to setRecipe with RecipeLayoutBuilder:\n{}", recipeInfo, e);
			return new Extraction(builder.buildIngredientSupplier(), false);
		}

		return new Extraction(builder.buildIngredientSupplier(), true);
	}

	public record Extraction(IIngredientSupplier supplier, boolean complete) {}
}

package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class RecipeIdClipboardHandler {
	private RecipeIdClipboardHandler() {
	}

	public static <R> Optional<String> getRecipeId(IRecipeLayoutDrawable<R> recipeLayout) {
		IRecipeCategory<R> recipeCategory = recipeLayout.getRecipeCategory();
		R recipe = recipeLayout.getRecipe();
		return Optional.ofNullable(recipeCategory.getRegistryName(recipe))
			.map(ResourceLocation::toString);
	}

	public static List<String> getRecipeIdsForCopy(
		@Nullable IRecipeLayoutDrawable<?> recipeLayout,
		ITypedIngredient<?> typedIngredient,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory
	) {
		if (recipeLayout != null) {
			return getRecipeId(recipeLayout).stream().toList();
		}
		return getOutputRecipeIds(typedIngredient, recipeManager, focusFactory);
	}

	public static List<String> getOutputRecipeIds(
		ITypedIngredient<?> typedIngredient,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory
	) {
		IFocus<?> focus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, typedIngredient);
		List<IFocus<?>> focuses = List.of(focus);
		return recipeManager.createRecipeCategoryLookup()
			.limitFocus(focuses)
			.get()
			.flatMap(category -> getRecipeIds(category, focuses, recipeManager))
			.toList();
	}

	public static String toClipboardText(List<String> recipeIds) {
		return String.join("\n", recipeIds);
	}

	private static <R> Stream<String> getRecipeIds(
		IRecipeCategory<R> category,
		Collection<? extends IFocus<?>> focuses,
		IRecipeManager recipeManager
	) {
		return recipeManager.createRecipeLookup(category.getRecipeType())
			.limitFocus(focuses)
			.get()
			.map(category::getRegistryName)
			.filter(Objects::nonNull)
			.map(ResourceLocation::toString);
	}
}

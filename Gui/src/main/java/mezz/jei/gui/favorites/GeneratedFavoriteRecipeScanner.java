package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class GeneratedFavoriteRecipeScanner {
	private static final Logger LOGGER = LogManager.getLogger();

	private final IRecipeManager recipeManager;
	private final IFocusFactory focusFactory;
	private final IIngredientManager ingredientManager;

	public GeneratedFavoriteRecipeScanner(
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IIngredientManager ingredientManager
	) {
		this.recipeManager = recipeManager;
		this.focusFactory = focusFactory;
		this.ingredientManager = ingredientManager;
	}

	public void rebuild(FavoriteRecipeStore store) {
		store.clearGeneratedFavorites();
		Map<BookmarkIngredientKey, Set<FocusedRecipe>> recipesByOutput = new LinkedHashMap<>();
		recipeManager.createRecipeCategoryLookup()
			.get()
			.forEach(category -> collectCategoryRecipes(category, recipesByOutput));

		recipesByOutput.forEach((target, recipes) -> {
			if (recipes.size() == 1) {
				store.setGeneratedFavorite(target, recipes.iterator().next());
			}
		});
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void collectCategoryRecipes(
		IRecipeCategory<?> recipeCategory,
		Map<BookmarkIngredientKey, Set<FocusedRecipe>> recipesByOutput
	) {
		IRecipeCategory rawCategory = recipeCategory;
		recipeManager.createRecipeLookup(rawCategory.getRecipeType())
			.get()
			.forEach(recipe -> collectRecipeOutputs(rawCategory, recipe, recipesByOutput));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void collectRecipeOutputs(
		IRecipeCategory recipeCategory,
		Object recipe,
		Map<BookmarkIngredientKey, Set<FocusedRecipe>> recipesByOutput
	) {
		ResourceLocation recipeUid = recipeCategory.getRegistryName(recipe);
		if (recipeUid == null) {
			return;
		}
		Optional<IRecipeLayoutDrawable<?>> layout;
		try {
			layout = recipeManager.createRecipeLayoutDrawable(
					recipeCategory,
					recipe,
					focusFactory.getEmptyFocusGroup()
				)
				.map(recipeLayout -> (IRecipeLayoutDrawable<?>) recipeLayout);
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("Skipping generated favorite scan for recipe {} in category {}.", recipeUid, recipeCategory.getRecipeType(), e);
			return;
		}
		if (layout.isEmpty()) {
			return;
		}

		FocusedRecipe focusedRecipe = new FocusedRecipe(recipeCategory.getRecipeType().getUid(), recipeUid);
		layout.get()
			.getRecipeSlotsView()
			.getSlotViews(RecipeIngredientRole.OUTPUT)
			.forEach(slot -> collectSlotOutputs(slot, focusedRecipe, recipesByOutput));
	}

	private void collectSlotOutputs(
		IRecipeSlotView slot,
		FocusedRecipe focusedRecipe,
		Map<BookmarkIngredientKey, Set<FocusedRecipe>> recipesByOutput
	) {
		slot.getAllIngredients()
			.map(this::createKey)
			.forEach(target -> recipesByOutput
				.computeIfAbsent(target, ignored -> new LinkedHashSet<>())
				.add(focusedRecipe));
	}

	private BookmarkIngredientKey createKey(ITypedIngredient<?> ingredient) {
		return BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager);
	}
}

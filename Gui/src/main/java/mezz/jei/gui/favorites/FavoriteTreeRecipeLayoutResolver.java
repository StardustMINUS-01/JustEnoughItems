package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.recipes.FocusedRecipeLayoutResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves {@link FocusedRecipe}s into recipe layouts for the favorite tree,
 * ported from JEI 1.21.1
 * ({@code mezz.jei.gui.favorites.FavoriteTreeRecipeLayoutResolver}).
 * The 1.21.1-only {@code GtmVirtualCircuitCompat} non-consumable input
 * projection (which has no equivalent in 1.20.1) is omitted.
 */
public final class FavoriteTreeRecipeLayoutResolver implements FavoriteTreeBuilder.RecipeResolver {
	private final IFocusFactory focusFactory;
	private final IIngredientManager ingredientManager;
	private final FocusedRecipeLayoutResolver focusedRecipeLayoutResolver;

	public FavoriteTreeRecipeLayoutResolver(
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IIngredientManager ingredientManager
	) {
		this.focusFactory = focusFactory;
		this.ingredientManager = ingredientManager;
		this.focusedRecipeLayoutResolver = new FocusedRecipeLayoutResolver(recipeManager);
	}

	@Override
	public Optional<FavoriteTreeBuilder.ResolvedRecipe> resolve(FocusedRecipe recipe) {
		return resolve(recipe, new RecipeLayoutBuildCache());
	}

	@Override
	public Optional<FavoriteTreeBuilder.ResolvedRecipe> resolve(
		FocusedRecipe recipe,
		RecipeLayoutBuildCache layoutCache
	) {
		return layoutCache.getOrBuild(recipe, () -> resolveLayout(recipe))
			.map(layout -> new FavoriteTreeBuilder.ResolvedRecipe(
				recipe,
				resolveInputs(layout),
				Optional.of(layout)
			));
	}

	public Optional<IRecipeLayoutDrawable<?>> resolveLayout(FocusedRecipe focusedRecipe) {
		return focusedRecipeLayoutResolver.resolve(focusedRecipe, focusFactory.getEmptyFocusGroup());
	}

	private List<FavoriteTreeBuilder.ResolvedInput> resolveInputs(IRecipeLayoutDrawable<?> layout) {
		List<IRecipeSlotView> inputSlots = layout.getRecipeSlotsView()
			.getSlotViews(RecipeIngredientRole.INPUT);
		// The 1.21.1 implementation subtracts GTM virtual-circuit non-consumable
		// inputs here via GtmVirtualCircuitCompat; no such compat exists in 1.20.1.
		Set<BookmarkIngredientKey> nonConsumableInputs = Set.of();
		List<FavoriteTreeBuilder.ResolvedInput> resolvedInputs = new ArrayList<>();
		for (int i = 0; i < inputSlots.size(); i++) {
			resolveInput(i, inputSlots.get(i), nonConsumableInputs)
				.ifPresent(resolvedInputs::add);
		}
		return resolvedInputs;
	}

	private Optional<FavoriteTreeBuilder.ResolvedInput> resolveInput(
		int inputSlotIndex,
		IRecipeSlotView slot,
		Set<BookmarkIngredientKey> nonConsumableInputs
	) {
		List<ITypedIngredient<?>> ingredients = slot.getAllIngredients()
			.filter(ingredient -> !nonConsumableInputs.contains(createKey(ingredient)))
			.toList();
		if (ingredients.isEmpty()) {
			return Optional.empty();
		}
		ITypedIngredient<?> displayed = slot.getDisplayedIngredient().orElse(ingredients.get(0));
		BookmarkIngredientKey displayedKey = createKey(displayed);
		List<BookmarkIngredientKey> permutationKeys = ingredients.stream()
			.map(this::createKey)
			.toList();
		return Optional.of(new FavoriteTreeBuilder.ResolvedInput(
			inputSlotIndex,
			displayedKey,
			permutationKeys,
			ingredients
		));
	}

	private BookmarkIngredientKey createKey(ITypedIngredient<?> ingredient) {
		return BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager);
	}
}

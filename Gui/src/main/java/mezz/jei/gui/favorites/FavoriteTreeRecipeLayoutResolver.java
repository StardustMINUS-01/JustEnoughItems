package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.compat.gtm.GtmVirtualCircuitCompat;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.recipes.FocusedRecipeLayoutResolver;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
		return resolveLayout(recipe)
			.map(layout -> new FavoriteTreeBuilder.ResolvedRecipe(recipe, resolveInputs(layout)));
	}

	public Optional<IRecipeLayoutDrawable<?>> resolveLayout(FocusedRecipe focusedRecipe) {
		return focusedRecipeLayoutResolver.resolve(focusedRecipe, focusFactory.getEmptyFocusGroup());
	}

	List<FavoriteTreeBuilder.ResolvedInput> resolveInputs(IRecipeLayoutDrawable<?> layout) {
		List<IRecipeSlotView> inputSlots = layout.getRecipeSlotsView()
			.getSlotViews(RecipeIngredientRole.INPUT);
		Set<BookmarkIngredientKey> nonConsumableInputs = GtmVirtualCircuitCompat
			.projectVirtualInputs(layout.getRecipe(), ingredientManager)
			.inputs()
			.stream()
			.map(GtmVirtualCircuitCompat.VirtualInput::ingredient)
			.map(this::createKey)
			.collect(Collectors.toUnmodifiableSet());
		return inputSlots.stream()
			.map(slot -> resolveInput(slot, nonConsumableInputs))
			.flatMap(Optional::stream)
			.toList();
	}

	private Optional<FavoriteTreeBuilder.ResolvedInput> resolveInput(
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
		return Optional.of(new FavoriteTreeBuilder.ResolvedInput(displayedKey, permutationKeys));
	}

	private BookmarkIngredientKey createKey(ITypedIngredient<?> ingredient) {
		return BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager);
	}
}

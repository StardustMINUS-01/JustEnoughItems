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
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.match.IngredientMatchInfo;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GeneratedFavoriteRecipeScanner {
	private static final Logger LOGGER = LogManager.getLogger();

	private final IRecipeManager recipeManager;
	private final IFocusFactory focusFactory;
	private final IIngredientManager ingredientManager;
	private final Map<BookmarkIngredientKey, List<RecipePreferenceCandidate>> candidatesByOutput = new LinkedHashMap<>();

	public GeneratedFavoriteRecipeScanner(
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IIngredientManager ingredientManager
	) {
		this.recipeManager = recipeManager;
		this.focusFactory = focusFactory;
		this.ingredientManager = ingredientManager;
	}

	public void rebuild(FavoriteRecipeStore store, RecipePreferenceRules recipePreferenceRules) {
		store.clearGeneratedFavorites();
		candidatesByOutput.clear();
		Map<BookmarkIngredientKey, OutputRecipeCandidates> recipesByOutput = new LinkedHashMap<>();
		recipeManager.createRecipeCategoryLookup()
			.get()
			.forEach(category -> collectCategoryRecipes(category, recipesByOutput));

		recipesByOutput.forEach((target, candidates) -> {
			candidatesByOutput.put(target, candidates.recipes());
			resolveGeneratedFavorite(candidates.recipes(), recipePreferenceRules)
				.ifPresent(recipe -> store.setGeneratedFavorite(target, recipe));
		});
	}

	public Map<BookmarkIngredientKey, List<RecipePreferenceCandidate>> getCandidatesByOutput() {
		return Map.copyOf(candidatesByOutput);
	}

	public static Optional<FocusedRecipe> resolveGeneratedFavorite(
		List<RecipePreferenceCandidate> recipes,
		RecipePreferenceRules recipePreferenceRules
	) {
		if (recipes.size() == 1) {
			return Optional.of(recipes.getFirst().recipe());
		}
		return recipePreferenceRules.resolvePreferredRecipe(recipes);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void collectCategoryRecipes(
		IRecipeCategory<?> recipeCategory,
		Map<BookmarkIngredientKey, OutputRecipeCandidates> recipesByOutput
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
		Map<BookmarkIngredientKey, OutputRecipeCandidates> recipesByOutput
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
		List<IngredientMatchInfo> inputs = layout.get()
			.getRecipeSlotsView()
			.getSlotViews(RecipeIngredientRole.INPUT)
			.stream()
			.flatMap(slot -> slot.getAllIngredients())
			.map(IngredientMatchInfo::fromIngredient)
			.flatMap(Optional::stream)
			.toList();
		List<IngredientMatchInfo> outputs = layout.get()
			.getRecipeSlotsView()
			.getSlotViews(RecipeIngredientRole.OUTPUT)
			.stream()
			.flatMap(slot -> slot.getAllIngredients())
			.map(IngredientMatchInfo::fromIngredient)
			.flatMap(Optional::stream)
			.toList();
		layout.get()
			.getRecipeSlotsView()
			.getSlotViews(RecipeIngredientRole.OUTPUT)
			.forEach(slot -> collectSlotOutputs(slot, focusedRecipe, inputs, outputs, recipesByOutput));
	}

	private void collectSlotOutputs(
		IRecipeSlotView slot,
		FocusedRecipe focusedRecipe,
		List<IngredientMatchInfo> inputs,
		List<IngredientMatchInfo> outputs,
		Map<BookmarkIngredientKey, OutputRecipeCandidates> recipesByOutput
	) {
		slot.getAllIngredients()
			.forEach(ingredient -> {
				BookmarkIngredientKey target = createKey(ingredient);
				recipesByOutput
					.computeIfAbsent(target, ignored -> new OutputRecipeCandidates())
					.add(focusedRecipe, inputs, outputs);
			});
	}

	private BookmarkIngredientKey createKey(ITypedIngredient<?> ingredient) {
		return BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager);
	}

	private static final class OutputRecipeCandidates {
		private final Map<FocusedRecipe, RecipePreferenceCandidate> recipes = new LinkedHashMap<>();

		private List<RecipePreferenceCandidate> recipes() {
			return List.copyOf(recipes.values());
		}

		private void add(
			FocusedRecipe recipe,
			List<IngredientMatchInfo> inputs,
			List<IngredientMatchInfo> outputs
		) {
			recipes.putIfAbsent(recipe, new RecipePreferenceCandidate(recipe, inputs, outputs));
		}
	}
}

package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.search.ISearchStorageBuilderFactory;
import mezz.jei.common.search.BakedSubstringIndexBuilder;
import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.match.IngredientMatchInfo;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RecipeLookupSnapshotFactory {
	private final IRecipeManager recipeManager;
	private final IIngredientManager ingredientManager;
	private final ISearchStorageBuilderFactory searchStorageBuilderFactory;

	public RecipeLookupSnapshotFactory(IRecipeManager recipeManager, IIngredientManager ingredientManager) {
		this(recipeManager, ingredientManager, BakedSubstringIndexBuilder::new);
	}

	public RecipeLookupSnapshotFactory(
		IRecipeManager recipeManager,
		IIngredientManager ingredientManager,
		ISearchStorageBuilderFactory searchStorageBuilderFactory
	) {
		this.recipeManager = recipeManager;
		this.ingredientManager = ingredientManager;
		this.searchStorageBuilderFactory = searchStorageBuilderFactory;
	}

	public RecipeLookupSnapshot create(ILookupState state) {
		return new RecipeLookupSnapshot(createCategories(state, false), searchStorageBuilderFactory);
	}

	public RecipeLookupSnapshot create(ILookupState state, RecipePreferenceRules preferenceRules) {
		return new RecipeLookupSnapshot(createCategories(state, true), preferenceRules, searchStorageBuilderFactory);
	}

	private List<RecipeLookupSnapshot.CategoryRecipes<?>> createCategories(
		ILookupState state,
		boolean includePreferenceCandidates
	) {
		List<RecipeLookupSnapshot.CategoryRecipes<?>> categories = new ArrayList<>();
		for (IRecipeCategory<?> category : state.getRecipeCategories()) {
			categories.add(createCategory(state, category, includePreferenceCandidates));
		}
		return categories;
	}

	private <T> RecipeLookupSnapshot.CategoryRecipes<T> createCategory(
		ILookupState state,
		IRecipeCategory<T> category,
		boolean includePreferenceCandidates
	) {
		IFocusedRecipes<T> focusedRecipes = castFocusedRecipes(state.getFocusedRecipes(category));
		List<RecipeSearchIngredient> catalysts = recipeManager.createRecipeCatalystLookup(category.getRecipeType())
			.get()
			.map(this::createSearchIngredient)
			.toList();
		List<RecipeLookupSnapshot.RecipeEntry<T>> entries = focusedRecipes.getRecipes().stream()
			.map(recipe -> createEntry(category, recipe, catalysts, includePreferenceCandidates))
			.toList();
		return new RecipeLookupSnapshot.CategoryRecipes<>(category, entries);
	}

	@SuppressWarnings("unchecked")
	private static <T> IFocusedRecipes<T> castFocusedRecipes(IFocusedRecipes<?> focusedRecipes) {
		return (IFocusedRecipes<T>) focusedRecipes;
	}

	private <T> RecipeLookupSnapshot.RecipeEntry<T> createEntry(
		IRecipeCategory<T> category,
		T recipe,
		List<RecipeSearchIngredient> catalysts,
		boolean includePreferenceCandidate
	) {
		IIngredientSupplier ingredients = recipeManager.getRecipeIngredients(category, recipe);
		List<ITypedIngredient<?>> inputs = ingredients.getIngredients(RecipeIngredientRole.INPUT);
		List<ITypedIngredient<?>> outputs = ingredients.getIngredients(RecipeIngredientRole.OUTPUT);
		ResourceLocation recipeUid = category.getRegistryName(recipe);

		List<String> recipeText = new ArrayList<>();
		recipeText.add(category.getTitle().getString());
		recipeText.add(category.getRecipeType().getUid().toString());
		if (recipeUid != null) {
			recipeText.add(recipeUid.toString());
		}

		RecipeSearchDocument document = new RecipeSearchDocument(
			inputs.stream().map(this::createSearchIngredient).toList(),
			outputs.stream().map(this::createSearchIngredient).toList(),
			catalysts,
			recipeText
		);
		Optional<RecipePreferenceCandidate> preferenceCandidate = includePreferenceCandidate ?
			createPreferenceCandidate(category, recipeUid, inputs, outputs) :
			Optional.empty();
		return new RecipeLookupSnapshot.RecipeEntry<>(recipe, document, preferenceCandidate);
	}

	private Optional<RecipePreferenceCandidate> createPreferenceCandidate(
		IRecipeCategory<?> category,
		ResourceLocation recipeUid,
		List<ITypedIngredient<?>> inputs,
		List<ITypedIngredient<?>> outputs
	) {
		if (recipeUid == null) {
			return Optional.empty();
		}
		FocusedRecipe focusedRecipe = new FocusedRecipe(category.getRecipeType().getUid(), recipeUid);
		return Optional.of(new RecipePreferenceCandidate(
			focusedRecipe,
			createIngredientMatchInfo(inputs),
			createIngredientMatchInfo(outputs)
		));
	}

	private static List<IngredientMatchInfo> createIngredientMatchInfo(List<ITypedIngredient<?>> ingredients) {
		return ingredients.stream()
			.map(IngredientMatchInfo::fromIngredient)
			.flatMap(Optional::stream)
			.toList();
	}

	private <T> RecipeSearchIngredient createSearchIngredient(ITypedIngredient<T> typedIngredient) {
		return RecipeSearchIngredientFactory.create(typedIngredient, ingredientManager);
	}
}

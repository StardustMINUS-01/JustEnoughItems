package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.search.ISearchStorage;
import mezz.jei.api.search.ISearchStorageBuilder;
import mezz.jei.api.search.ISearchStorageBuilderFactory;
import mezz.jei.common.search.BakedSubstringIndexBuilder;
import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class RecipeLookupSnapshot {
	private final List<CategoryRecipes<?>> categories;
	private final Set<FocusedRecipe> preferredRecipes;
	private final ISearchStorageBuilderFactory searchStorageBuilderFactory;
	private ISearchStorage<String> searchStorage;

	public RecipeLookupSnapshot(List<CategoryRecipes<?>> categories) {
		this(categories, BakedSubstringIndexBuilder::new);
	}

	public RecipeLookupSnapshot(
		List<CategoryRecipes<?>> categories,
		ISearchStorageBuilderFactory searchStorageBuilderFactory
	) {
		this.categories = List.copyOf(categories);
		this.preferredRecipes = Set.of();
		this.searchStorageBuilderFactory = searchStorageBuilderFactory;
	}

	public RecipeLookupSnapshot(List<CategoryRecipes<?>> categories, RecipePreferenceRules preferenceRules) {
		this(categories, preferenceRules, BakedSubstringIndexBuilder::new);
	}

	public RecipeLookupSnapshot(
		List<CategoryRecipes<?>> categories,
		RecipePreferenceRules preferenceRules,
		ISearchStorageBuilderFactory searchStorageBuilderFactory
	) {
		this.categories = List.copyOf(categories);
		List<RecipePreferenceCandidate> candidates = categories.stream()
			.flatMap(category -> category.entries().stream())
			.map(RecipeEntry::preferenceCandidate)
			.flatMap(Optional::stream)
			.toList();
		this.preferredRecipes = new HashSet<>(preferenceRules.resolvePreferredRecipes(candidates));
		this.searchStorageBuilderFactory = searchStorageBuilderFactory;
	}

	public List<IFocusedRecipes<?>> project(RecipeFilterMode mode, RecipeSearchQuery query) {
		return project(mode, query, IRecipeSearchTextMatcher.DEFAULT);
	}

	public List<IFocusedRecipes<?>> project(
		RecipeFilterMode mode,
		RecipeSearchQuery query,
		IRecipeSearchTextMatcher matcher
	) {
		return categories.stream()
			.map(category -> project(category, mode, query, matcher))
			.flatMap(Optional::stream)
			.toList();
	}

	public IRecipeSearchTextMatcher createSearchTextMatcher() {
		if (searchStorage == null) {
			searchStorage = createSearchStorage();
		}
		return IRecipeSearchTextMatcher.create(searchStorage);
	}

	private ISearchStorage<String> createSearchStorage() {
		ISearchStorageBuilder<String> builder = searchStorageBuilderFactory.create("recipe_filter_strings");
		Set<String> indexedStrings = new HashSet<>();
		categories.stream()
			.flatMap(category -> category.entries().stream())
			.map(RecipeEntry::searchDocument)
			.forEach(document -> addSearchDocument(builder, indexedStrings, document));
		return builder.build();
	}

	private static void addSearchDocument(
		ISearchStorageBuilder<String> builder,
		Set<String> indexedStrings,
		RecipeSearchDocument document
	) {
		document.inputs().forEach(ingredient -> addSearchIngredient(builder, indexedStrings, ingredient));
		document.outputs().forEach(ingredient -> addSearchIngredient(builder, indexedStrings, ingredient));
		document.catalysts().forEach(ingredient -> addSearchIngredient(builder, indexedStrings, ingredient));
		document.recipeText().forEach(text -> {
			addSearchString(builder, indexedStrings, text);
			int separator = text.indexOf(':');
			if (separator >= 0) {
				addSearchString(builder, indexedStrings, text.substring(0, separator));
			}
		});
	}

	private static void addSearchIngredient(
		ISearchStorageBuilder<String> builder,
		Set<String> indexedStrings,
		RecipeSearchIngredient ingredient
	) {
		addSearchString(builder, indexedStrings, ingredient.displayName());
		addSearchString(builder, indexedStrings, ingredient.resourceLocation());
		addSearchString(builder, indexedStrings, ingredient.modId());
		ingredient.tags().forEach(tag -> addSearchString(builder, indexedStrings, tag));
	}

	private static void addSearchString(
		ISearchStorageBuilder<String> builder,
		Set<String> indexedStrings,
		String text
	) {
		if (indexedStrings.add(text)) {
			builder.put(text, text);
		}
	}

	private <T> Optional<IFocusedRecipes<?>> project(
		CategoryRecipes<T> category,
		RecipeFilterMode mode,
		RecipeSearchQuery query,
		IRecipeSearchTextMatcher matcher
	) {
		List<T> recipes = category.entries().stream()
			.filter(entry -> matchesMode(entry, mode))
			.filter(entry -> query.matches(entry.searchDocument(), matcher))
			.map(RecipeEntry::recipe)
			.toList();
		if (recipes.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new StaticFocusedRecipes<>(category.category(), recipes));
	}

	private boolean matchesMode(RecipeEntry<?> entry, RecipeFilterMode mode) {
		if (mode == RecipeFilterMode.ALL) {
			return true;
		}
		Optional<RecipePreferenceCandidate> candidate = entry.preferenceCandidate();
		if (candidate.isEmpty()) {
			return mode == RecipeFilterMode.NOT_PREFERRED;
		}
		boolean preferred = preferredRecipes.contains(candidate.get().recipe());
		return mode == RecipeFilterMode.PREFERRED ? preferred : !preferred;
	}

	public record CategoryRecipes<T>(
		IRecipeCategory<T> category,
		List<RecipeEntry<T>> entries
	) {
		public CategoryRecipes {
			entries = List.copyOf(entries);
		}
	}

	public record RecipeEntry<T>(
		T recipe,
		RecipeSearchDocument searchDocument,
		Optional<RecipePreferenceCandidate> preferenceCandidate
	) {
	}
}

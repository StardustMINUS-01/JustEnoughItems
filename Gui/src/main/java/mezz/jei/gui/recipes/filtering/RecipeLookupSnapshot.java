package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.recipe.category.IRecipeCategory;
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

	public RecipeLookupSnapshot(List<CategoryRecipes<?>> categories) {
		this.categories = List.copyOf(categories);
		this.preferredRecipes = Set.of();
	}

	public RecipeLookupSnapshot(List<CategoryRecipes<?>> categories, RecipePreferenceRules preferenceRules) {
		this.categories = List.copyOf(categories);
		List<RecipePreferenceCandidate> candidates = categories.stream()
			.flatMap(category -> category.entries().stream())
			.map(RecipeEntry::preferenceCandidate)
			.flatMap(Optional::stream)
			.toList();
		this.preferredRecipes = new HashSet<>(preferenceRules.resolvePreferredRecipes(candidates));
	}

	public List<IFocusedRecipes<?>> project(RecipeFilterMode mode, RecipeSearchQuery query) {
		return categories.stream()
			.map(category -> project(category, mode, query))
			.flatMap(Optional::stream)
			.toList();
	}

	private <T> Optional<IFocusedRecipes<?>> project(
		CategoryRecipes<T> category,
		RecipeFilterMode mode,
		RecipeSearchQuery query
	) {
		List<T> recipes = category.entries().stream()
			.filter(entry -> matchesMode(entry, mode))
			.filter(entry -> query.matches(entry.searchDocument()))
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

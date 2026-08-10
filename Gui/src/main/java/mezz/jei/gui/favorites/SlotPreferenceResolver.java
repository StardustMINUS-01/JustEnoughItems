package mezz.jei.gui.favorites;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class SlotPreferenceResolver implements SlotRuleResolver {
	private final GeneratedFavoriteRecipeScanner scanner;
	private final Supplier<RecipePreferenceRules> rulesSupplier;

	public SlotPreferenceResolver(
		GeneratedFavoriteRecipeScanner scanner,
		Supplier<RecipePreferenceRules> rulesSupplier
	) {
		this.scanner = scanner;
		this.rulesSupplier = rulesSupplier;
	}

	@Override
	public Optional<FocusedRecipe> resolveSlot(List<BookmarkIngredientKey> variants) {
		RecipePreferenceRules rules = rulesSupplier.get();
		if (rules.isEmpty() || variants.isEmpty()) {
			return Optional.empty();
		}
		Map<FocusedRecipe, RecipePreferenceCandidate> merged = new LinkedHashMap<>();
		for (BookmarkIngredientKey variant : variants) {
			for (RecipePreferenceCandidate candidate : scanner.getCandidatesByOutput()
				.getOrDefault(variant, List.of())) {
				merged.putIfAbsent(candidate.recipe(), candidate);
			}
		}
		if (merged.isEmpty()) {
			return Optional.empty();
		}
		return rules.resolvePreferredRecipe(List.copyOf(merged.values()));
	}
}

package mezz.jei.gui.favorites;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.favorites.preferences.RecipePreferenceIngredientInfo;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Slot-level preference resolution backed by the generated favorite scan data.
 * Merges the output candidates of every variant in the slot and lets the preference
 * rules collapse them to a single recipe.
 */
public final class SlotPreferenceResolver implements SlotRuleResolver {
	private final GeneratedFavoriteRecipeScanner scanner;
	private final RecipePreferenceRules rules;

	public SlotPreferenceResolver(
		GeneratedFavoriteRecipeScanner scanner,
		RecipePreferenceRules rules
	) {
		this.scanner = scanner;
		this.rules = rules;
	}

	@Override
	public Optional<FocusedRecipe> resolveSlot(List<BookmarkIngredientKey> variants) {
		if (rules.isEmpty() || variants.isEmpty()) {
			return Optional.empty();
		}
		List<RecipePreferenceIngredientInfo> variantInfos = new ArrayList<>(variants.size());
		Map<FocusedRecipe, RecipePreferenceCandidate> merged = new LinkedHashMap<>();
		for (BookmarkIngredientKey variant : variants) {
			Optional<RecipePreferenceIngredientInfo> info = Optional.ofNullable(
				scanner.getTargetInfoByOutput().get(variant)
			);
			if (info.isEmpty()) {
				// A variant without any output info cannot take part in a unique
				// slot-level collapse, so the slot is not considered unique.
				return Optional.empty();
			}
			variantInfos.add(info.get());
			for (RecipePreferenceCandidate candidate : scanner.getCandidatesByOutput()
				.getOrDefault(variant, List.of())) {
				merged.putIfAbsent(candidate.recipe(), candidate);
			}
		}
		if (merged.isEmpty()) {
			return Optional.empty();
		}
		return rules.resolvePreferredRecipeForSlot(variantInfos, List.copyOf(merged.values()));
	}
}

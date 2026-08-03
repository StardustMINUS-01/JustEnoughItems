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
		List<RecipePreferenceIngredientInfo> variantInfos = new ArrayList<>(variants.size());
		Map<FocusedRecipe, RecipePreferenceCandidate> merged = new LinkedHashMap<>();
		for (BookmarkIngredientKey variant : variants) {
			Optional<RecipePreferenceIngredientInfo> info = Optional.ofNullable(
				scanner.getTargetInfoByOutput().get(variant)
			);
			if (info.isEmpty()) {
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

package mezz.jei.gui.favorites;

import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class SlotPreferenceResolver implements SlotRuleResolver {
	private final RecipePreferenceCandidateResolver resolver;
	private final Supplier<RecipePreferenceRules> rulesSupplier;

	public SlotPreferenceResolver(
		RecipePreferenceCandidateResolver resolver,
		Supplier<RecipePreferenceRules> rulesSupplier
	) {
		this.resolver = resolver;
		this.rulesSupplier = rulesSupplier;
	}

	@Override
	public Optional<FocusedRecipe> resolveSlot(List<SlotVariant> variants) {
		return resolveSlot(variants, new RecipeLayoutBuildCache());
	}

	@Override
	public Optional<FocusedRecipe> resolveSlot(
		List<SlotVariant> variants,
		RecipeLayoutBuildCache layoutCache
	) {
		RecipePreferenceRules rules = rulesSupplier.get();
		if (rules.isEmpty() || variants.isEmpty()) {
			return Optional.empty();
		}
		Map<FocusedRecipe, RecipePreferenceCandidate> merged = new LinkedHashMap<>();
		for (SlotVariant variant : variants) {
			for (RecipePreferenceCandidate candidate : resolver.getCandidates(variant.key(), variant.ingredient(), layoutCache)) {
				merged.putIfAbsent(candidate.recipe(), candidate);
			}
		}
		if (merged.isEmpty()) {
			return Optional.empty();
		}
		return rules.resolvePreferredRecipe(List.copyOf(merged.values()));
	}
}

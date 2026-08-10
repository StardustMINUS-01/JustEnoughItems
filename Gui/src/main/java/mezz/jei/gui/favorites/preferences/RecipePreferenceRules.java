package mezz.jei.gui.favorites.preferences;

import mezz.jei.gui.input.FocusedRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

public final class RecipePreferenceRules {
	public static final RecipePreferenceRules EMPTY = new RecipePreferenceRules(List.of());

	private final List<RecipePreferenceRule> rules;

	public RecipePreferenceRules(List<RecipePreferenceRule> rules) {
		this.rules = List.copyOf(rules);
	}

	public Optional<FocusedRecipe> resolvePreferredRecipe(List<RecipePreferenceCandidate> candidates) {
		List<RecipePreferenceCandidate> uniqueCandidates = candidates.stream()
			.collect(Collectors.toMap(
				RecipePreferenceCandidate::recipe,
				candidate -> candidate,
				(first, second) -> first,
				LinkedHashMap::new
			))
			.values()
			.stream()
			.toList();
		if (uniqueCandidates.size() == 1) {
			return Optional.of(uniqueCandidates.getFirst().recipe());
		}
		for (RecipePreferenceRule rule : rules) {
			Optional<FocusedRecipe> selected = resolve(rule, uniqueCandidates);
			if (selected.isPresent()) {
				return selected;
			}
		}
		return Optional.empty();
	}

	public boolean isEmpty() {
		return rules.isEmpty();
	}

	private static Optional<FocusedRecipe> resolve(
		RecipePreferenceRule rule,
		List<RecipePreferenceCandidate> candidates
	) {
		List<RankedCandidate> ranked = new ArrayList<>();
		for (RecipePreferenceCandidate candidate : candidates) {
			OptionalInt outputRank = rule.output().rank(candidate.outputs());
			if (outputRank.isEmpty()) {
				continue;
			}
			OptionalInt inputRank = rule.input()
				.map(expression -> expression.rank(candidate.inputs()))
				.orElse(OptionalInt.empty());
			OptionalInt recipeRank = rule.recipe()
				.map(expression -> expression.rank(candidate.recipe().recipeUid()))
				.orElse(OptionalInt.empty());
			ranked.add(new RankedCandidate(candidate, outputRank, inputRank, recipeRank));
		}
		boolean inputActive = ranked.stream().anyMatch(candidate -> candidate.inputRank().isPresent());
		boolean recipeActive = ranked.stream().anyMatch(candidate -> candidate.recipeRank().isPresent());
		List<RankedCandidate> eligible = ranked.stream()
			.filter(candidate -> !inputActive || candidate.inputRank().isPresent())
			.filter(candidate -> !recipeActive || candidate.recipeRank().isPresent())
			.toList();
		List<RankedCandidate> best = eligible.stream()
			.filter(candidate -> eligible.stream().noneMatch(other -> dominates(other, candidate, inputActive, recipeActive)))
			.toList();
		return best.size() == 1 ? Optional.of(best.getFirst().candidate().recipe()) : Optional.empty();
	}

	private static boolean dominates(
		RankedCandidate first,
		RankedCandidate second,
		boolean inputActive,
		boolean recipeActive
	) {
		if (first == second) {
			return false;
		}
		boolean strictlyBetter = false;
		int outputComparison = Integer.compare(first.outputRank().getAsInt(), second.outputRank().getAsInt());
		if (outputComparison > 0) {
			return false;
		}
		strictlyBetter |= outputComparison < 0;
		if (inputActive) {
			int comparison = Integer.compare(first.inputRank().getAsInt(), second.inputRank().getAsInt());
			if (comparison > 0) {
				return false;
			}
			strictlyBetter |= comparison < 0;
		}
		if (recipeActive) {
			int comparison = Integer.compare(first.recipeRank().getAsInt(), second.recipeRank().getAsInt());
			if (comparison > 0) {
				return false;
			}
			strictlyBetter |= comparison < 0;
		}
		return strictlyBetter;
	}

	private record RankedCandidate(
		RecipePreferenceCandidate candidate,
		OptionalInt outputRank,
		OptionalInt inputRank,
		OptionalInt recipeRank
	) {
	}
}

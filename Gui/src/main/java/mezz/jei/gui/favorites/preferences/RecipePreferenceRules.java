package mezz.jei.gui.favorites.preferences;

import mezz.jei.gui.input.FocusedRecipe;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

public class RecipePreferenceRules {
	public static final RecipePreferenceRules EMPTY = new RecipePreferenceRules(List.of());

	private final List<CompiledRule> rules;

	public RecipePreferenceRules(List<RecipePreferenceRule> rules) {
		this.rules = rules.stream()
			.map(CompiledRule::new)
			.toList();
	}

	public Optional<FocusedRecipe> resolvePreferredRecipe(
		RecipePreferenceIngredientInfo target,
		List<RecipePreferenceCandidate> candidates
	) {
		for (CompiledRule rule : rules) {
			if (!rule.rule().target().matches(target)) {
				continue;
			}
			Optional<FocusedRecipe> selected = rule.resolve(candidates);
			if (selected.isPresent()) {
				return selected;
			}
		}
		return Optional.empty();
	}

	public boolean isEmpty() {
		return rules.isEmpty();
	}

	private record CompiledRule(
		RecipePreferenceRule rule,
		List<List<Pattern>> recipeTiers
	) {
		private CompiledRule(RecipePreferenceRule rule) {
			this(rule, rule.recipeTiers().stream()
				.map(tier -> tier.stream().map(CompiledRule::compileWildcard).toList())
				.toList());
		}

		private Optional<FocusedRecipe> resolve(List<RecipePreferenceCandidate> candidates) {
			List<RankedCandidate> ranked = candidates.stream()
				.filter(candidate -> matchesRecipeType(candidate.recipe()))
				.map(candidate -> new RankedCandidate(candidate, getInputRank(candidate), getRecipeRank(candidate)))
				.toList();
			boolean inputActive = ranked.stream().anyMatch(candidate -> candidate.inputRank().isPresent());
			boolean recipeActive = ranked.stream().anyMatch(candidate -> candidate.recipeRank().isPresent());
			if (!inputActive && !recipeActive) {
				return Optional.empty();
			}
			List<RankedCandidate> eligible = ranked.stream()
				.filter(candidate -> !inputActive || candidate.inputRank().isPresent())
				.filter(candidate -> !recipeActive || candidate.recipeRank().isPresent())
				.toList();
			List<RankedCandidate> best = eligible.stream()
				.filter(candidate -> eligible.stream().noneMatch(other -> dominates(other, candidate, inputActive, recipeActive)))
				.toList();
			return best.size() == 1 ? Optional.of(best.getFirst().candidate().recipe()) : Optional.empty();
		}

		private boolean matchesRecipeType(FocusedRecipe recipe) {
			return rule.recipeType()
				.map(recipe.recipeTypeUid()::equals)
				.orElse(true);
		}

		private OptionalInt getInputRank(RecipePreferenceCandidate candidate) {
			for (int i = 0; i < rule.inputTiers().size(); i++) {
				List<RecipePreferenceTarget> tier = rule.inputTiers().get(i);
				if (!tier.isEmpty() && tier.stream().allMatch(selector -> candidate.inputs().stream().anyMatch(selector::matches))) {
					return OptionalInt.of(i);
				}
			}
			return OptionalInt.empty();
		}

		private OptionalInt getRecipeRank(RecipePreferenceCandidate candidate) {
			for (int i = 0; i < recipeTiers.size(); i++) {
				List<Pattern> tier = recipeTiers.get(i);
				if (!tier.isEmpty() && tier.stream().anyMatch(pattern -> pattern.matcher(candidate.recipe().recipeUid().toString()).matches())) {
					return OptionalInt.of(i);
				}
			}
			return OptionalInt.empty();
		}

		private static boolean dominates(RankedCandidate first, RankedCandidate second, boolean inputActive, boolean recipeActive) {
			if (first == second) {
				return false;
			}
			boolean strictlyBetter = false;
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

		private static Pattern compileWildcard(String wildcard) {
			StringBuilder regex = new StringBuilder();
			for (int i = 0; i < wildcard.length(); i++) {
				char c = wildcard.charAt(i);
				if (c == '*') {
					regex.append(".*");
				} else {
					regex.append(Pattern.quote(String.valueOf(c)));
				}
			}
			return Pattern.compile(regex.toString());
		}

		private record RankedCandidate(
			RecipePreferenceCandidate candidate,
			OptionalInt inputRank,
			OptionalInt recipeRank
		) {
		}
	}
}

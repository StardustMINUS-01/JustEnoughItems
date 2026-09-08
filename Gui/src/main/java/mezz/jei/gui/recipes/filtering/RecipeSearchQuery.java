package mezz.jei.gui.recipes.filtering;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecipeSearchQuery {
	private final List<List<SearchTerm>> alternatives;
	private final List<List<SearchTerm>> inputAlternatives;

	private RecipeSearchQuery(List<List<SearchTerm>> alternatives) {
		this.alternatives = alternatives;
		List<List<SearchTerm>> inputAlternatives = alternatives.stream()
			.map(alternative -> alternative.stream()
				.filter(term -> term.scope() == Scope.INPUT || term.scope() == Scope.ALL)
				.toList())
			.toList();
		this.inputAlternatives = inputAlternatives.stream().anyMatch(List::isEmpty) ?
			List.of() :
			inputAlternatives;
	}

	public static RecipeSearchQuery parse(String query) {
		List<List<SearchTerm>> alternatives = new ArrayList<>();
		List<SearchTerm> current = new ArrayList<>();
		for (String token : tokenize(query)) {
			if (token.equals("|")) {
				if (!current.isEmpty()) {
					alternatives.add(List.copyOf(current));
					current.clear();
				}
			} else {
				current.add(SearchTerm.parse(token));
			}
		}
		if (!current.isEmpty()) {
			alternatives.add(List.copyOf(current));
		}
		return new RecipeSearchQuery(List.copyOf(alternatives));
	}

	public boolean isEmpty() {
		return alternatives.isEmpty();
	}

	public boolean matches(RecipeSearchDocument document) {
		return matches(document, IRecipeSearchTextMatcher.DEFAULT);
	}

	boolean matches(RecipeSearchDocument document, IRecipeSearchTextMatcher matcher) {
		return isEmpty() || alternatives.stream()
			.anyMatch(alternative -> alternative.stream().allMatch(term -> term.matches(document, matcher)));
	}

	public boolean hasInputTerms() {
		return !inputAlternatives.isEmpty();
	}

	public boolean matchesInputCandidate(RecipeSearchIngredient ingredient) {
		return matchesInputCandidate(ingredient, IRecipeSearchTextMatcher.DEFAULT);
	}

	public boolean matchesInputCandidate(RecipeSearchIngredient ingredient, IRecipeSearchTextMatcher matcher) {
		return inputAlternatives.isEmpty() || inputAlternatives.stream()
			.anyMatch(alternative -> alternative.stream().allMatch(term -> term.matchesIngredient(ingredient, matcher)));
	}

	private static List<String> tokenize(String query) {
		List<String> tokens = new ArrayList<>();
		StringBuilder token = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < query.length(); i++) {
			char character = query.charAt(i);
			if (character == '"') {
				quoted = !quoted;
			} else if (!quoted && (Character.isWhitespace(character) || character == '|')) {
				if (!token.isEmpty()) {
					tokens.add(token.toString());
					token.setLength(0);
				}
				if (character == '|') {
					tokens.add("|");
				}
			} else {
				token.append(character);
			}
		}
		if (!token.isEmpty()) {
			tokens.add(token.toString());
		}
		return tokens;
	}

	private enum Scope {
		ALL,
		INPUT,
		OUTPUT,
		CATALYST,
		RECIPE
	}

	private enum MatchType {
		TEXT,
		TAG,
		MOD,
		RESOURCE_LOCATION
	}

	private record SearchTerm(Scope scope, MatchType matchType, String value, boolean excluded) {
		private static SearchTerm parse(String token) {
			boolean excluded = token.startsWith("-") && token.length() > 1;
			if (excluded) {
				token = token.substring(1);
			}
			Scope scope = Scope.ALL;
			if (token.length() > 2 && token.charAt(1) == ':') {
				scope = switch (Character.toLowerCase(token.charAt(0))) {
					case 'i' -> Scope.INPUT;
					case 'o' -> Scope.OUTPUT;
					case 'c' -> Scope.CATALYST;
					case 'r' -> Scope.RECIPE;
					default -> Scope.ALL;
				};
				if (scope != Scope.ALL) {
					token = token.substring(2);
				}
			}
			MatchType matchType = MatchType.TEXT;
			if (token.length() > 1) {
				matchType = switch (token.charAt(0)) {
					case '#' -> MatchType.TAG;
					case '@' -> MatchType.MOD;
					case '&' -> MatchType.RESOURCE_LOCATION;
					default -> MatchType.TEXT;
				};
				if (matchType != MatchType.TEXT) {
					token = token.substring(1);
				}
			}
			return new SearchTerm(scope, matchType, token.toLowerCase(Locale.ROOT), excluded);
		}

		private boolean matches(RecipeSearchDocument document, IRecipeSearchTextMatcher matcher) {
			boolean matched = switch (scope) {
				case ALL -> matchesIngredients(document.inputs(), matcher) ||
					matchesIngredients(document.outputs(), matcher) ||
					matchesIngredients(document.catalysts(), matcher) ||
					matchesRecipeText(document, matcher);
				case INPUT -> matchesIngredients(document.inputs(), matcher);
				case OUTPUT -> matchesIngredients(document.outputs(), matcher);
				case CATALYST -> matchesIngredients(document.catalysts(), matcher);
				case RECIPE -> matchesRecipeText(document, matcher);
			};
			return excluded != matched;
		}

		private boolean matchesIngredients(
			List<RecipeSearchIngredient> ingredients,
			IRecipeSearchTextMatcher matcher
		) {
			return ingredients.stream().anyMatch(ingredient -> matchesIngredientValue(ingredient, matcher));
		}

		private boolean matchesIngredient(RecipeSearchIngredient ingredient, IRecipeSearchTextMatcher matcher) {
			return excluded != matchesIngredientValue(ingredient, matcher);
		}

		private boolean matchesIngredientValue(RecipeSearchIngredient ingredient, IRecipeSearchTextMatcher matcher) {
			return switch (matchType) {
				case TEXT -> ingredient.matchesText(value, matcher);
				case TAG -> ingredient.matchesTag(value, matcher);
				case MOD -> ingredient.matchesMod(value, matcher);
				case RESOURCE_LOCATION -> ingredient.matchesResourceLocation(value, matcher);
			};
		}

		private boolean matchesRecipeText(RecipeSearchDocument document, IRecipeSearchTextMatcher matcher) {
			if (matchType == MatchType.TAG) {
				return false;
			}
			return document.recipeText().stream().anyMatch(text -> switch (matchType) {
				case TEXT -> matcher.contains(text, value);
				case MOD -> matcher.contains(getNamespace(text), value);
				case RESOURCE_LOCATION -> matcher.contains(text, value);
				case TAG -> false;
			});
		}

		private static String getNamespace(String value) {
			int separator = value.indexOf(':');
			return separator < 0 ? "" : value.substring(0, separator);
		}
	}
}

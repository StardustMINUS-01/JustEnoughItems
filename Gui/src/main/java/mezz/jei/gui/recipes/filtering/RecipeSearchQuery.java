package mezz.jei.gui.recipes.filtering;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecipeSearchQuery {
	private final List<List<SearchTerm>> alternatives;

	private RecipeSearchQuery(List<List<SearchTerm>> alternatives) {
		this.alternatives = alternatives;
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
		return isEmpty() || alternatives.stream()
			.anyMatch(alternative -> alternative.stream().allMatch(term -> term.matches(document)));
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

		private boolean matches(RecipeSearchDocument document) {
			boolean matched = switch (scope) {
				case ALL -> matchesIngredients(document.inputs()) ||
					matchesIngredients(document.outputs()) ||
					matchesIngredients(document.catalysts()) ||
					matchesRecipeText(document);
				case INPUT -> matchesIngredients(document.inputs());
				case OUTPUT -> matchesIngredients(document.outputs());
				case CATALYST -> matchesIngredients(document.catalysts());
				case RECIPE -> matchesRecipeText(document);
			};
			return excluded != matched;
		}

		private boolean matchesIngredients(List<RecipeSearchIngredient> ingredients) {
			return ingredients.stream().anyMatch(ingredient -> switch (matchType) {
				case TEXT -> ingredient.matchesText(value);
				case TAG -> ingredient.matchesTag(value);
				case MOD -> ingredient.matchesMod(value);
				case RESOURCE_LOCATION -> ingredient.matchesResourceLocation(value);
			});
		}

		private boolean matchesRecipeText(RecipeSearchDocument document) {
			if (matchType == MatchType.TAG) {
				return false;
			}
			return document.recipeText().stream().anyMatch(text -> switch (matchType) {
				case TEXT -> text.contains(value);
				case MOD -> getNamespace(text).contains(value);
				case RESOURCE_LOCATION -> text.contains(value);
				case TAG -> false;
			});
		}

		private static String getNamespace(String value) {
			int separator = value.indexOf(':');
			return separator < 0 ? "" : value.substring(0, separator);
		}
	}
}

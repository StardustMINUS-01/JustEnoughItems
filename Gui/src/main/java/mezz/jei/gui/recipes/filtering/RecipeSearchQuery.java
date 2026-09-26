package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import org.jetbrains.annotations.Nullable;

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
		this.inputAlternatives = inputAlternatives.stream().anyMatch(List::isEmpty) ? List.of() : inputAlternatives;
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

	public RecipeSearchQuery withDefaultScope(RecipeSearchScope scope) {
		if (scope == RecipeSearchScope.NONE)
			return this;
		Scope selected = Scope.valueOf(scope.name());
		return new RecipeSearchQuery(alternatives.stream().map(terms -> terms.stream()
			.map(term -> term.scope() == Scope.ALL ? new SearchTerm(selected, term.matchType(), term.value(), term.excluded()) : term).toList())
			.toList());
	}

	public java.util.Set<String> getTokens() {
		return alternatives.stream().flatMap(List::stream).map(SearchTerm::value).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public <T> boolean matches(IRecipeCategory<T> category, T recipe, IRecipeManager recipes, IIngredientManager ingredients,
		java.util.function.Supplier<List<ITypedIngredient<?>>> catalysts) {
		return matches(category, recipe, recipes, ingredients, catalysts, IRecipeSearchTextMatcher.DEFAULT);
	}

	<T> boolean matches(IRecipeCategory<T> category, T recipe, IRecipeManager recipes, IIngredientManager ingredients,
		java.util.function.Supplier<List<ITypedIngredient<?>>> catalysts, IRecipeSearchTextMatcher matcher) {
		if (isEmpty())
			return true;
		RecipeMaterials<T> materials = new RecipeMaterials<>(category, recipe, recipes, ingredients, catalysts, matcher);
		for (List<SearchTerm> alternative : alternatives) {
			boolean matches = true;
			for (SearchTerm term : alternative) {
				if (term.excluded() == materials.matches(term)) {
					matches = false;
					break;
				}
			}
			if (matches)
				return true;
		}
		return false;
	}

	private static final class RecipeMaterials<T> {
		private final IRecipeCategory<T> category;
		private final T recipe;
		private final IRecipeManager recipes;
		private final IIngredientManager ingredients;
		private final java.util.function.Supplier<List<ITypedIngredient<?>>> catalysts;
		private @Nullable IIngredientSupplier materials;
		private final IRecipeSearchTextMatcher matcher;

		private RecipeMaterials(IRecipeCategory<T> category, T recipe, IRecipeManager recipes, IIngredientManager ingredients,
			java.util.function.Supplier<List<ITypedIngredient<?>>> catalysts, IRecipeSearchTextMatcher matcher) {
			this.category = category;
			this.recipe = recipe;
			this.recipes = recipes;
			this.ingredients = ingredients;
			this.catalysts = catalysts;
			this.matcher = matcher;
		}

		private boolean matches(SearchTerm term) {
			return switch (term.scope()) {
				case ALL -> matchesRole(RecipeIngredientRole.INPUT, term) || matchesRole(RecipeIngredientRole.OUTPUT, term) ||
					matchesIngredients(catalysts.get(), term) || matchesRecipe(term);
				case INPUT -> matchesRole(RecipeIngredientRole.INPUT, term);
				case OUTPUT -> matchesRole(RecipeIngredientRole.OUTPUT, term);
				case CATALYST -> matchesIngredients(catalysts.get(), term);
				case RECIPE -> matchesRecipe(term);
			};
		}

		private boolean matchesRole(RecipeIngredientRole role, SearchTerm term) {
			if (materials == null)
				materials = recipes.getRecipeIngredients(category, recipe);
			return matchesIngredients(materials.getIngredients(role), term);
		}

		private boolean matchesIngredients(List<ITypedIngredient<?>> candidates, SearchTerm term) {
			for (ITypedIngredient<?> candidate : candidates) {
				if (matchesIngredient(candidate, term))
					return true;
			}
			return false;
		}

		private <I> boolean matchesIngredient(ITypedIngredient<I> candidate, SearchTerm term) {
			IIngredientHelper<I> helper = ingredients.getIngredientHelper(candidate.getType());
			I ingredient = candidate.getIngredient();
			return switch (term.matchType()) {
				case TEXT -> contains(helper.getDisplayName(ingredient), term.value()) ||
					contains(helper.getResourceLocation(ingredient).toString(), term.value()) ||
					contains(helper.getDisplayModId(ingredient), term.value()) ||
					helper.getTagStream(ingredient).anyMatch(tag -> contains(tag.toString(), term.value()));
				case TAG -> helper.getTagStream(ingredient).anyMatch(tag -> contains(tag.toString(), term.value()));
				case MOD -> contains(helper.getDisplayModId(ingredient), term.value());
				case RESOURCE_LOCATION -> contains(helper.getResourceLocation(ingredient).toString(), term.value());
			};
		}

		private boolean matchesRecipe(SearchTerm term) {
			if (term.matchType() == MatchType.TAG)
				return false;
			if (matchesRecipeText(category.getTitle().getString(), term) || matchesRecipeText(category.getRecipeType().getUid().toString(), term))
				return true;
			var id = category.getRegistryName(recipe);
			return id != null && matchesRecipeText(id.toString(), term);
		}

		private boolean matchesRecipeText(String text, SearchTerm term) {
			if (term.matchType() == MatchType.MOD)
				text = SearchTerm.getNamespace(text);
			return contains(text, term.value());
		}

		private boolean contains(String text, String value) {
			return matcher.contains(text.toLowerCase(Locale.ROOT), value);
		}
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

		private static String getNamespace(String value) {
			int separator = value.indexOf(':');
			return separator < 0 ? "" : value.substring(0, separator);
		}
	}
}

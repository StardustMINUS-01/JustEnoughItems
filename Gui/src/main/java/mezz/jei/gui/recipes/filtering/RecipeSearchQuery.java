package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.util.Translator;
import mezz.jei.gui.search.SearchTokenizer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiPredicate;

public final class RecipeSearchQuery {
	private final List<List<SearchTerm>> alternatives;
	private final List<List<SearchTerm>> inputAlternatives;
	private final Set<RecipeIngredientRole> ingredientRoles;

	private RecipeSearchQuery(List<List<SearchTerm>> alternatives) {
		this.alternatives = alternatives;
		List<List<SearchTerm>> inputAlternatives = alternatives.stream()
			.map(alternative -> alternative.stream()
				.filter(term -> term.scope() == Scope.INPUT || term.scope() == Scope.ALL)
				.toList())
			.toList();
		this.inputAlternatives = inputAlternatives.stream().anyMatch(List::isEmpty) ? List.of() : inputAlternatives;
		Set<RecipeIngredientRole> roles = EnumSet.noneOf(RecipeIngredientRole.class);
		for (List<SearchTerm> alternative : alternatives) {
			for (SearchTerm term : alternative) {
				if (term.scope() == Scope.INPUT || term.scope() == Scope.ALL)
					roles.add(RecipeIngredientRole.INPUT);
				if (term.scope() == Scope.OUTPUT || term.scope() == Scope.ALL)
					roles.add(RecipeIngredientRole.OUTPUT);
			}
		}
		this.ingredientRoles = Set.copyOf(roles);
	}

	public static RecipeSearchQuery parse(String query) {
		List<List<SearchTerm>> alternatives = new ArrayList<>();
		List<SearchTerm> current = new ArrayList<>();
		for (var token : SearchTokenizer.tokenize(query, RecipeSearchQuery::isSearchPrefix)) {
			if (token.operator()) {
				if (!current.isEmpty()) {
					alternatives.add(List.copyOf(current));
					current.clear();
				}
			} else {
				current.add(SearchTerm.parse(token.text(), token.exclusion()));
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

	public Set<String> getRecipeTokens() {
		return alternatives.stream().flatMap(List::stream).filter(term -> term.scope() == Scope.RECIPE || term.scope() == Scope.ALL)
			.map(SearchTerm::value).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public Set<String> getIngredientTokens() {
		return alternatives.stream().flatMap(List::stream).filter(term -> term.scope() != Scope.RECIPE)
			.map(SearchTerm::ingredientToken).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public <T> boolean matches(IRecipeCategory<T> category, T recipe, IRecipeManager recipes,
		java.util.function.Supplier<List<ITypedIngredient<?>>> catalysts, IRecipeSearchTextMatcher matcher,
		BiPredicate<ITypedIngredient<?>, String> ingredientMatcher) {
		if (isEmpty())
			return true;
		RecipeMaterials<T> materials = new RecipeMaterials<>(category, recipe, recipes, catalysts, matcher, ingredientMatcher, ingredientRoles);
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
		private final BiPredicate<ITypedIngredient<?>, String> ingredientMatcher;
		private final java.util.function.Supplier<List<ITypedIngredient<?>>> catalysts;
		private @Nullable IIngredientSupplier materials;
		private final IRecipeSearchTextMatcher matcher;
		private final Set<RecipeIngredientRole> roles;

		private RecipeMaterials(IRecipeCategory<T> category, T recipe, IRecipeManager recipes,
			java.util.function.Supplier<List<ITypedIngredient<?>>> catalysts, IRecipeSearchTextMatcher matcher,
			BiPredicate<ITypedIngredient<?>, String> ingredientMatcher, Set<RecipeIngredientRole> roles) {
			this.category = category;
			this.recipe = recipe;
			this.recipes = recipes;
			this.ingredientMatcher = ingredientMatcher;
			this.catalysts = catalysts;
			this.matcher = matcher;
			this.roles = roles;
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
				materials = recipes.getRecipeIngredients(category, recipe, roles);
			return matchesIngredients(materials.getIngredients(role), term);
		}

		private boolean matchesIngredients(List<ITypedIngredient<?>> candidates, SearchTerm term) {
			for (ITypedIngredient<?> candidate : candidates) {
				if (ingredientMatcher.test(candidate, term.ingredientToken()))
					return true;
			}
			return false;
		}

		private boolean matchesRecipe(SearchTerm term) {
			if (term.matchType() == MatchType.TAG)
				return false;
			if (matchesRecipeText(category.getTitle().getString(), term) || matchesRecipeText(category.getRecipeType().getUid().toString(), term))
				return true;
			var id = category.getRegistryName(recipe);
			return id != null && matchesRecipeText(id.toString(), term) ||
				category.getSourceRecipeIds(recipe).stream().anyMatch(sourceId -> matchesRecipeText(sourceId.toString(), term));
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

	public boolean matchesInputCandidate(ITypedIngredient<?> ingredient, BiPredicate<ITypedIngredient<?>, String> matcher) {
		return inputAlternatives.isEmpty() || inputAlternatives.stream()
			.anyMatch(alternative -> alternative.stream().allMatch(term -> term.excluded() != matcher.test(ingredient, term.ingredientToken())));
	}

	private static boolean isSearchPrefix(String text) {
		if (text.length() >= 2 && text.charAt(1) == ':' && "iocr".indexOf(Character.toLowerCase(text.charAt(0))) >= 0) {
			text = text.substring(2);
			if (text.isEmpty())
				return true;
		}
		return text.length() == 1 && !Character.isLetterOrDigit(text.charAt(0)) && text.charAt(0) != '-' && text.charAt(0) != '|';
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
		private static SearchTerm parse(String token, boolean excluded) {
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
			return new SearchTerm(scope, matchType, Translator.toLowercaseWithLocale(token), excluded);
		}

		private String ingredientToken() {
			return switch (matchType) {
				case TEXT -> value;
				case TAG -> "#" + value;
				case MOD -> "@" + value;
				case RESOURCE_LOCATION -> "&" + value;
			};
		}

		private static String getNamespace(String value) {
			int separator = value.indexOf(':');
			return separator < 0 ? "" : value.substring(0, separator);
		}
	}
}

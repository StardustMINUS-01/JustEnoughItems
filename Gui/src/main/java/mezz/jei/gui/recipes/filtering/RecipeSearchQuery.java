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
import java.util.function.Supplier;
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

	boolean onlyMatchesCatalysts() {
		return !isEmpty() && alternatives.stream().allMatch(terms -> terms.stream().allMatch(term -> term.scope == Scope.CATALYST));
	}

	Set<RecipeIngredientRole> ingredientRoles() { return ingredientRoles; }

	public Set<String> getRecipeTokens() {
		return alternatives.stream().flatMap(List::stream).filter(term -> term.scope() == Scope.RECIPE || term.scope() == Scope.ALL)
			.map(SearchTerm::value).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public Set<String> getIngredientTokens() {
		return alternatives.stream().flatMap(List::stream).filter(term -> term.scope() != Scope.RECIPE)
			.map(SearchTerm::ingredientToken).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public <T> boolean matches(IRecipeCategory<T> category, T recipe, IRecipeManager recipes,
		Supplier<List<ITypedIngredient<?>>> catalysts, IRecipeSearchTextMatcher matcher,
		BiPredicate<ITypedIngredient<?>, String> ingredientMatcher) {
		return matches(new RecipeMaterials<>(category, recipe, recipes, catalysts, matcher, ingredientMatcher, ingredientRoles));
	}

	<T> boolean matchesCached(IRecipeCategory<T> category, T recipe, IIngredientSupplier supplier,
		IRecipeSearchTextMatcher matcher, BiPredicate<ITypedIngredient<?>, String> ingredientMatcher) {
		var context = new RecipeMaterials<>(category, recipe, null, null, matcher, ingredientMatcher, ingredientRoles);
		context.materials = supplier;
		return matches(context);
	}

	<T> boolean matchesDynamic(IRecipeCategory<T> category, T recipe, IRecipeManager recipes, IRecipeSearchTextMatcher matcher,
		BiPredicate<ITypedIngredient<?>, String> ingredientMatcher, BiPredicate<List<ITypedIngredient<?>>, String> roleMatcher) {
		var context = new RecipeMaterials<>(category, recipe, recipes, null, matcher, ingredientMatcher, ingredientRoles);
		context.roleMatcher = roleMatcher;
		return matches(context);
	}

	private boolean matches(RecipeMaterials<?> materials) {
		if (isEmpty())
			return true;
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
		private final Supplier<List<ITypedIngredient<?>>> catalysts;
		private @Nullable IIngredientSupplier materials;
		private @Nullable BiPredicate<List<ITypedIngredient<?>>, String> roleMatcher;
		private final IRecipeSearchTextMatcher matcher;
		private final Set<RecipeIngredientRole> roles;

		private RecipeMaterials(IRecipeCategory<T> category, T recipe, IRecipeManager recipes,
			Supplier<List<ITypedIngredient<?>>> catalysts, IRecipeSearchTextMatcher matcher,
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
					(catalysts != null && matchesIngredients(catalysts.get(), term)) || matchesRecipe(term);
				case INPUT -> matchesRole(RecipeIngredientRole.INPUT, term);
				case OUTPUT -> matchesRole(RecipeIngredientRole.OUTPUT, term);
				case CATALYST -> matchesIngredients(catalysts.get(), term);
				case RECIPE -> matchesRecipe(term);
			};
		}

		private boolean matchesRole(RecipeIngredientRole role, SearchTerm term) {
			if (materials == null)
				materials = recipes.getRecipeIngredients(category, recipe, roles);
			var candidates = materials.getIngredients(role);
			return roleMatcher == null ? matchesIngredients(candidates, term) : roleMatcher.test(candidates, term.ingredientToken());
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
			if (catalysts != null && (matchesRecipeText(category.getTitle().getString(), term) || matchesRecipeText(category.getRecipeType().getUid().toString(), term)))
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

	boolean sameTerms(RecipeSearchQuery other) {
		return alternatives.equals(other.alternatives);
	}

	boolean narrows(RecipeSearchQuery previous, boolean nativeBackend) {
		if (alternatives.size() != 1 || previous.alternatives.size() != 1)
			return false;
		var terms = alternatives.getFirst();
		for (var old : previous.alternatives.getFirst()) {
			if (terms.contains(old))
				continue;
			if (!nativeBackend || old.excluded || old.value.isEmpty() ||
				!Character.isLetterOrDigit(old.value.codePointAt(0)) || Character.isHighSurrogate(old.value.charAt(old.value.length() - 1)) ||
				terms.stream().noneMatch(term -> !term.excluded && term.scope == old.scope && term.matchType == old.matchType && term.value.startsWith(old.value)))
				return false;
		}
		return true;
	}

	List<List<SearchTerm>> branches() {
		return alternatives;
	}

	record Compiled(boolean always, RecipeSearchQuery residual) {
		boolean constant() {
			return residual.isEmpty();
		}
	}

	Compiled compileForCategory(IRecipeCategory<?> category, Supplier<List<ITypedIngredient<?>>> catalysts,
		IRecipeSearchTextMatcher text, BiPredicate<ITypedIngredient<?>, String> ingredients) {
		List<List<SearchTerm>> branches = new ArrayList<>();
		String title = null, uid = null;
		for (var alternative : alternatives) {
			List<SearchTerm> terms = new ArrayList<>();
			boolean impossible = false;
			for (var term : alternative) {
				boolean categoryHit = false;
				if ((term.scope == Scope.ALL || term.scope == Scope.RECIPE) && term.matchType != MatchType.TAG) {
					if (title == null) {
						title = category.getTitle().getString().toLowerCase(Locale.ROOT);
						uid = category.getRecipeType().getUid().toString();
					}
					categoryHit = term.matchesText(title, text) || term.matchesText(uid, text);
				}
				if (!categoryHit && (term.scope == Scope.ALL || term.scope == Scope.CATALYST))
					categoryHit = catalysts.get().stream().anyMatch(i -> ingredients.test(i, term.ingredientToken));
				if (categoryHit || term.scope == Scope.CATALYST || term.scope == Scope.RECIPE && term.matchType == MatchType.TAG) {
					if (categoryHit == term.excluded) {
						impossible = true;
						break;
					}
				} else {
					terms.add(term);
				}
			}
			if (!impossible) {
				if (terms.isEmpty())
					return new Compiled(true, EMPTY);
				branches.add(List.copyOf(terms));
			}
		}
		return new Compiled(isEmpty(), branches.isEmpty() ? EMPTY : new RecipeSearchQuery(List.copyOf(branches)));
	}

	private static final RecipeSearchQuery EMPTY = new RecipeSearchQuery(List.of());

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

	enum Scope {
		ALL,
		INPUT,
		OUTPUT,
		CATALYST,
		RECIPE
	}

	enum MatchType {
		TEXT,
		TAG,
		MOD,
		RESOURCE_LOCATION
	}

	record SearchTerm(Scope scope, MatchType matchType, String value, boolean excluded, String ingredientToken) {
		SearchTerm(Scope scope, MatchType matchType, String value, boolean excluded) {
			this(scope, matchType, value, excluded, switch (matchType) {
				case TEXT -> value;
				case TAG -> "#" + value;
				case MOD -> "@" + value;
				case RESOURCE_LOCATION -> "&" + value;
			});
		}

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

		boolean canMatchResourceId() {
			if ((scope != Scope.ALL && scope != Scope.RECIPE) || matchType == MatchType.TAG)
				return false;
			for (int i = 0; i < value.length(); i++)
				if (value.charAt(i) > 127)
					return false;
			return true;
		}

		boolean matchesText(String lower, IRecipeSearchTextMatcher matcher) {
			return matcher.contains(matchType == MatchType.MOD ? getNamespace(lower) : lower, value);
		}

		private static String getNamespace(String value) {
			int separator = value.indexOf(':');
			return separator < 0 ? "" : value.substring(0, separator);
		}
	}
}

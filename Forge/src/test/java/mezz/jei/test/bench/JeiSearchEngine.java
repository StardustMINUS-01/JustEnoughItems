package mezz.jei.test.bench;

import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.search.ISearchStorageBuilder;
import mezz.jei.api.search.ISearchStorageBuilderFactory;
import mezz.jei.common.search.GeneralizedSuffixTreeSearchStorage;
import mezz.jei.common.search.SearchStorageBuilderAdapter;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.ingredients.IngredientListElementFactory;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.ElementSearch;
import mezz.jei.gui.search.SearchTokenizer;
import mezz.jei.gui.search.Token;
import mezz.jei.library.ingredients.subtypes.SubtypeInterpreters;
import mezz.jei.library.ingredients.subtypes.SubtypeManager;
import mezz.jei.library.load.registration.IngredientManagerBuilder;
import mezz.jei.test.lib.TestColorHelper;
import mezz.jei.test.lib.TestIngredient;
import mezz.jei.test.lib.TestIngredientFilterConfig;
import mezz.jei.test.lib.TestModIdHelper;
import mezz.jei.test.lib.TestPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A minimal, self-contained JEI search engine used by the reference and boundary
 * benchmark suites. It mirrors the real JEI search pipeline
 * (SearchTokenizer -> ElementPrefixParser -> ElementSearch) against synthetic
 * TestIngredients, so the numbers measured here reflect the actual suffix-tree
 * and intersection code paths that run in-game.
 */
public final class JeiSearchEngine {
	private static final TestModIdHelper MOD_ID_HELPER = new TestModIdHelper();
	private static final TestColorHelper COLOR_HELPER = new TestColorHelper();
	private static final TestIngredientFilterConfig FILTER_CONFIG = new TestIngredientFilterConfig();

	private final IIngredientManager ingredientManager;
	private final ElementPrefixParser elementPrefixParser;
	private final ElementSearch search;
	private final SearchTokenizer tokenizer = new SearchTokenizer();
	private final int scale;

	private JeiSearchEngine(
		IIngredientManager ingredientManager,
		ElementPrefixParser elementPrefixParser,
		ElementSearch search,
		int scale
	) {
		this.ingredientManager = ingredientManager;
		this.elementPrefixParser = elementPrefixParser;
		this.search = search;
		this.scale = scale;
	}

	/**
	 * Builds an engine indexing {@code scale} synthetic ingredients
	 * (base plugin ingredients 0 and 1 plus extra ingredients 2..scale-1).
	 *
	 * @return the engine plus the index build time in nanoseconds
	 */
	public static EngineResult create(int scale) {
		SubtypeInterpreters subtypeInterpreters = new SubtypeInterpreters();
		SubtypeManager subtypeManager = new SubtypeManager(subtypeInterpreters);
		IngredientManagerBuilder builder = new IngredientManagerBuilder(subtypeManager, COLOR_HELPER);
		new TestPlugin().registerIngredients(builder);

		// Base plugin ingredients are 0 and 1; add 2..scale-1 as extras.
		if (scale > TestPlugin.BASE_INGREDIENT_COUNT) {
			List<TestIngredient> extras = new ArrayList<>(scale - TestPlugin.BASE_INGREDIENT_COUNT);
			for (int i = TestPlugin.BASE_INGREDIENT_COUNT; i < scale; i++) {
				extras.add(new TestIngredient(i));
			}
			builder.addExtraIngredients(TestIngredient.TYPE, extras);
		}

		IIngredientManager ingredientManager = builder.build();
		ElementPrefixParser elementPrefixParser = new ElementPrefixParser(
			ingredientManager,
			FILTER_CONFIG,
			COLOR_HELPER,
			MOD_ID_HELPER,
			new ISearchStorageBuilderFactory() {
				@Override
				public <T> ISearchStorageBuilder<T> create() {
					return new SearchStorageBuilderAdapter<>(new GeneralizedSuffixTreeSearchStorage<>());
				}
			}
		);

		long start = System.nanoTime();
		List<IListElementInfo<?>> baseList = IngredientListElementFactory.createBaseList(ingredientManager, MOD_ID_HELPER);
		ElementSearch search = new ElementSearch(elementPrefixParser, baseList, ingredientManager);
		long buildNanos = System.nanoTime() - start;

		return new EngineResult(new JeiSearchEngine(ingredientManager, elementPrefixParser, search, scale), buildNanos);
	}

	/**
	 * Full pipeline search, mirroring {@code IngredientFilter.getSearchResults}:
	 * tokenize, parse prefixes, intersect positive tokens, apply exclusions.
	 *
	 * @return the matching ingredient numbers
	 */
	public Set<Integer> search(String query) {
		// Mirror IngredientFilter.getElements(): lowercase before tokenizing.
		String normalized = query.toLowerCase(Locale.ROOT);
		List<Token> tokens = tokenizer.tokenize(normalized);
		List<ElementPrefixParser.TokenInfo> toSearch = new ArrayList<>();
		List<ElementPrefixParser.TokenInfo> toRemove = new ArrayList<>();
		for (Token token : tokens) {
			if (token.isEmpty()) {
				continue;
			}
			elementPrefixParser.parseToken(token.text())
				.ifPresent(result -> {
					if (token.exclusion()) {
						toRemove.add(result);
					} else {
						toSearch.add(result);
					}
				});
		}

		List<Set<IListElement<?>>> resultsPerToken = toSearch.stream()
			.map(search::getSearchResults)
			.toList();
		Set<IListElement<?>> results = intersection(resultsPerToken);

		if (results.isEmpty() && !toRemove.isEmpty()) {
			results.addAll(search.getAllIngredients());
		}

		if (!results.isEmpty() && !toRemove.isEmpty()) {
			for (ElementPrefixParser.TokenInfo tokenInfo : toRemove) {
				Set<IListElement<?>> resultsToRemove = search.getSearchResults(tokenInfo);
				results.removeAll(resultsToRemove);
				if (results.isEmpty()) {
					break;
				}
			}
		}

		return results.stream()
			.map(this::ingredientNumber)
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/**
	 * Single-token search for a raw token (used by boundary suite).
	 */
	public Set<Integer> searchToken(String token) {
		ElementPrefixParser.TokenInfo tokenInfo = elementPrefixParser.parseToken(token.toLowerCase(Locale.ROOT))
			.orElse(new ElementPrefixParser.TokenInfo("", elementPrefixParser.getNoPrefix()));
		Set<IListElement<?>> results = search.getSearchResults(tokenInfo);
		return results.stream()
			.map(this::ingredientNumber)
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	public int getScale() {
		return scale;
	}

	public int getIndexedCount() {
		return search.getAllIngredients().size();
	}

	private int ingredientNumber(IListElement<?> element) {
		TestIngredient ingredient = (TestIngredient) element.getTypedIngredient().getIngredient();
		return ingredient.number();
	}

	private static <T> Set<T> intersection(List<Set<T>> sets) {
		Set<T> smallestSet = sets.stream()
			.min(java.util.Comparator.comparing(Set::size))
			.orElseGet(Set::of);

		Set<T> results = Collections.newSetFromMap(new IdentityHashMap<>());
		results.addAll(smallestSet);

		for (Set<T> set : sets) {
			if (set == smallestSet) {
				continue;
			}
			if (results.retainAll(set) && results.isEmpty()) {
				break;
			}
		}
		return results;
	}

	/** Result of {@link #create(int)}: the engine plus the index build time. */
	public record EngineResult(JeiSearchEngine engine, long buildNanos) {
		public double buildMs() {
			return buildNanos / 1_000_000.0;
		}
	}
}

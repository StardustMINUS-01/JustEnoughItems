package mezz.jei.gui.recipes.filtering;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import mezz.jei.api.search.ISearchStorageBuilder;
import mezz.jei.api.search.ISearchStorage;
import mezz.jei.api.search.ISearchStorageBuilderFactory;
import mezz.jei.common.search.BakedSubstringIndexBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Adapts the registered search backend to on-demand matching with a task-local cache. */
final class RecipeSearchTextMatcher implements IRecipeSearchTextMatcher {
	private final ISearchStorageBuilderFactory factory;
	private final Set<String> tokens;
	private final Cache<String, Set<String>> matches = CacheBuilder.newBuilder()
		.maximumWeight(262_144)
		.weigher((String text, Set<String> matched) -> (int) Math.min(Integer.MAX_VALUE, 32L + text.length() + 8L * matched.size()))
		.build();

	private RecipeSearchTextMatcher(ISearchStorageBuilderFactory factory, Set<String> tokens) {
		this.factory = factory;
		this.tokens = tokens;
	}

	static Supplier<IRecipeSearchTextMatcher> createFactory(ISearchStorageBuilderFactory factory, RecipeSearchQuery query) {
		synchronized (factory) {
			if (query.isEmpty() || factory.create("recipe_filter_strings").getClass() == BakedSubstringIndexBuilder.class)
				return () -> DEFAULT;
		}
		Set<String> tokens = query.getTokens();
		return () -> new RecipeSearchTextMatcher(factory, tokens);
	}

	@Override
	public boolean contains(String text, String token) {
		Set<String> matched = matches.getIfPresent(text);
		if (matched == null) {
			// A single-text index avoids stale negative results when later texts are encountered.
			ISearchStorage<String> storage;
			// Backend factories can maintain shared registries of the search instances they create.
			synchronized (factory) {
				ISearchStorageBuilder<String> builder = factory.create("recipe_filter_strings");
				builder.put(text, text);
				storage = builder.build();
			}
			Set<String> found = new HashSet<>();
			for (String queryToken : tokens) {
				storage.getSearchResults(queryToken, results -> {
					if (results.contains(text))
						found.add(queryToken);
				});
			}
			matched = Set.copyOf(found);
			matches.put(text, matched);
		}
		return matched.contains(token);
	}
}

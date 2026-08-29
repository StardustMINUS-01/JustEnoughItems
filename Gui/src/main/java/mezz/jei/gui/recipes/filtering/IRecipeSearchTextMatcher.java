package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.search.ISearchStorage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@FunctionalInterface
public interface IRecipeSearchTextMatcher {
	IRecipeSearchTextMatcher DEFAULT = String::contains;

	boolean contains(String text, String token);

	static IRecipeSearchTextMatcher create(ISearchStorage<String> searchStorage) {
		Map<String, Set<String>> resultsByToken = new HashMap<>();
		return (text, token) -> resultsByToken.computeIfAbsent(token, key -> {
			Set<String> results = new HashSet<>();
			searchStorage.getSearchResults(key, results::addAll);
			return results;
		}).contains(text);
	}
}

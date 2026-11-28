package mezz.jei.gui.recipes.filtering;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.ResourceLocation;

import java.util.BitSet;
import java.util.List;

/** Resource locations are lowercase ASCII. Paths are concatenated without retaining a String per recipe. */
final class RecipePathIndex {
	private final String[] namespaces;
	private final char[] namespaceIds;
	private final int[] wideNamespaceIds;
	private final RecipeRowOffsets recipeOffsets, pathOffsets;
	private final byte[] paths;

	<T> RecipePathIndex(IRecipeCategory<T> category, List<T> recipes, Runnable checkpoint) {
		var names = new Object2IntOpenHashMap<String>();
		var ids = new IntArrayList();
		var offsets = new IntArrayList();
		var bytes = new ByteArrayList();
		var starts = new int[recipes.size() + 1];
		for (int row = 0; row < recipes.size(); row++) {
			if ((row & 255) == 0)
				checkpoint.run();
			T recipe = recipes.get(row);
			var id = category.getRegistryName(recipe);
			if (id != null)
				append(id, names, ids, offsets, bytes);
			for (var source : category.getSourceRecipeIds(recipe))
				if (!source.equals(id))
					append(source, names, ids, offsets, bytes);
			starts[row + 1] = ids.size();
		}
		recipeOffsets = new RecipeRowOffsets(starts);
		namespaces = new String[names.size()];
		names.object2IntEntrySet().forEach(entry -> namespaces[entry.getIntValue()] = entry.getKey());
		if (names.size() <= Character.MAX_VALUE + 1) {
			namespaceIds = new char[ids.size()];
			for (int i = 0; i < namespaceIds.length; i++)
				namespaceIds[i] = (char) ids.getInt(i);
			wideNamespaceIds = null;
		} else {
			namespaceIds = null;
			wideNamespaceIds = ids.toIntArray();
		}
		offsets.add(bytes.size());
		pathOffsets = new RecipeRowOffsets(offsets.toIntArray());
		paths = bytes.toByteArray();
	}

	private static void append(ResourceLocation id, Object2IntOpenHashMap<String> names, IntArrayList ids, IntArrayList offsets, ByteArrayList bytes) {
		ids.add(names.computeIfAbsent(id.getNamespace(), key -> names.size()));
		offsets.add(bytes.size());
		String path = id.getPath();
		for (int i = 0; i < path.length(); i++)
			bytes.add((byte) path.charAt(i));
	}

	void addMatches(RecipeSearchQuery.SearchTerm term, BitSet candidates, BitSet result, Runnable checkpoint) {
		if (term.matchType() == RecipeSearchQuery.MatchType.TAG)
			return;
		String token = term.value();
		int colon = token.indexOf(':');
		boolean mod = term.matchType() == RecipeSearchQuery.MatchType.MOD;
		if (colon >= 0 && (mod || token.indexOf(':', colon + 1) >= 0))
			return;
		boolean[] accepted = new boolean[namespaces.length];
		String left = colon < 0 ? token : token.substring(0, colon);
		for (int i = 0; i < accepted.length; i++)
			accepted[i] = colon < 0 ? namespaces[i].contains(left) : namespaces[i].endsWith(left);
		String pattern = colon < 0 ? token : token.substring(colon + 1);
		boolean ascii = pattern.chars().allMatch(c -> c < 128);
		if (!ascii)
			return;
		result.or(RecipeRowFilter.matching(candidates, row -> {
			for (int i = recipeOffsets.get(row), end = recipeOffsets.get(row + 1); i < end; i++) {
				boolean ns = accepted[namespaceIds == null ? wideNamespaceIds[i] : namespaceIds[i]];
				if (colon < 0 && ns || !mod && (colon < 0 || ns) && pathMatches(i, pattern, colon >= 0)) {
					return true;
				}
			}
			return false;
		}, checkpoint));
	}

	private boolean pathMatches(int id, String token, boolean prefix) {
		int start = pathOffsets.get(id), limit = pathOffsets.get(id + 1) - token.length();
		if (prefix)
			limit = Math.min(limit, start);
		for (int pos = start; pos <= limit; pos++) {
			int j = 0;
			while (j < token.length() && paths[pos + j] == token.charAt(j))
				j++;
			if (j == token.length())
				return true;
		}
		return false;
	}
}

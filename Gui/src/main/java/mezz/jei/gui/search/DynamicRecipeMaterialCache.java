package mezz.jei.gui.search;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.search.ElementCorpus.IngredientKey;
import mezz.jei.gui.search.OrphanIngredientCorpus.Entry;

import java.util.LinkedHashMap;
import java.util.List;

/** Page-local IDs for the immutable role lists returned by the recipe material cache. */
public final class DynamicRecipeMaterialCache {
	private final OrphanIngredientCorpus orphans;
	private final Cache<List<ITypedIngredient<?>>, Materials> compiled;

	private record Materials(int[] ids, Entry[] extras, int lastStaticOrphan) {}

	public DynamicRecipeMaterialCache(OrphanIngredientCorpus orphans, int maximumWeight) {
		this.orphans = orphans;
		compiled = CacheBuilder.newBuilder().weakKeys().maximumWeight(maximumWeight)
			.weigher((List<ITypedIngredient<?>> key, Materials value) -> 1 + value.ids.length + value.extras.length)
			.build();
	}

	public boolean matches(List<ITypedIngredient<?>> values, String token, IngredientSearchSnapshot snapshot) {
		if (values.isEmpty())
			return false;
		var words = snapshot.getMaterialBits(token);
		if (words == null) {
			for (var ingredient : values)
				if (snapshot.test(ingredient, token))
					return true;
			return false;
		}
		var materials = compiled.getIfPresent(values);
		if (materials == null) {
			materials = compile(values, snapshot);
			compiled.put(values, materials);
		}
		for (int id : materials.ids)
			if (id >>> 6 < words.length && (words[id >>> 6] & (1L << id)) != 0)
				return true;
		var orphanWords = materials.lastStaticOrphan < 0 ? null : orphans.cachedWords(token, snapshot, materials.lastStaticOrphan);
		for (var extra : materials.extras)
			if (extra.id >= 0 && orphanWords != null ? (orphanWords[extra.id >>> 6] & (1L << extra.id)) != 0 : orphans.matchesDynamic(extra, snapshot, token))
				return true;
		return false;
	}

	private Materials compile(List<ITypedIngredient<?>> values, IngredientSearchSnapshot snapshot) {
		var ids = new IntOpenHashSet();
		var extras = new LinkedHashMap<IngredientKey, Entry>();
		for (var ingredient : values) {
			var key = snapshot.getKey(ingredient);
			int id = snapshot.elementId(key);
			if (id >= 0)
				ids.add(id);
			else
				extras.computeIfAbsent(key, k -> orphans.dynamicEntry(k, ingredient));
		}
		var entries = extras.values().toArray(Entry[]::new);
		int last = -1;
		for (var entry : entries)
			last = Math.max(last, entry.id);
		return new Materials(ids.toIntArray(), entries, last);
	}
}

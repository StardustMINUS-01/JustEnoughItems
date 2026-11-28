package mezz.jei.gui.search;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.ingredients.itemStacks.TypedItemStack;
import mezz.jei.common.search.PrefixInfo;
import mezz.jei.common.search.compact.CompactFieldIndex;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementCorpus.IngredientKey;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/** Static recipe ingredients outside the published element dictionary. Ids live as long as its recipe indexes. */
public final class OrphanIngredientCorpus {
	private static final int BLOCK = 512;
	private final IngredientSearchSnapshot base;
	private final int firstId;
	private final ConcurrentHashMap<IngredientKey, Entry> registered = new ConcurrentHashMap<>();
	private final ArrayList<Block> blocks = new ArrayList<>();
	private final Interner<Entry> dynamicEntries = Interners.newWeakInterner();
	private final Cache<IngredientKey, Entry> dynamic = CacheBuilder.newBuilder().weakValues().maximumSize(8192).build();
	private final Cache<Query, Hits> queries = CacheBuilder.newBuilder().maximumSize(8).build();

	public OrphanIngredientCorpus(IngredientSearchSnapshot base) {
		this.base = base;
		firstId = (base.elementCount() + 63) & ~63;
	}

	public int firstId() { return firstId; }

	public boolean canUseItemAsUid(TypedItemStack stack) {
		return base.canUseItemAsUid(stack);
	}

	public int resolve(ITypedIngredient<?> ingredient) {
		var key = base.getKey(ingredient);
		int regular = base.elementId(key);
		if (regular >= 0)
			return regular;
		Entry entry = registered.get(key);
		if (entry == null) {
			synchronized (this) {
				entry = registered.get(key);
				if (entry != null)
					return entry.id;
				int id = registered.size();
				if (id % BLOCK == 0)
					blocks.add(new Block());
				entry = new Entry(key, ingredient, firstId + id);
				blocks.get(id / BLOCK).entries[id % BLOCK] = entry;
				registered.put(key, entry);
			}
		}
		return entry.id;
	}

	boolean testExtra(IngredientKey key, ITypedIngredient<?> ingredient, String token, IngredientSearchSnapshot snapshot) {
		Entry entry = registered.get(key);
		if (entry == null)
			entry = dynamic.asMap().computeIfAbsent(key, k -> dynamicEntry(k, ingredient));
		return entry.matches(snapshot, token);
	}

	Entry dynamicEntry(IngredientKey key, ITypedIngredient<?> ingredient) {
		Entry entry = registered.get(key);
		return entry != null ? entry : dynamicEntries.intern(new Entry(key, ingredient, -1));
	}

	boolean matchesDynamic(Entry entry, IngredientSearchSnapshot snapshot, String token) {
		// A concurrent static index build may have registered this key since the list was compiled.
		if (entry.id < 0)
			entry = registered.getOrDefault(entry.key, entry);
		return entry.matches(snapshot, token);
	}

	public boolean test(int material, String token, IngredientSearchSnapshot snapshot) {
		int id = material - firstId;
		Entry entry;
		synchronized (this) {
			entry = blocks.get(id / BLOCK).entries[id % BLOCK];
		}
		return entry.matches(snapshot, token);
	}

	private record Query(String token, long revision, int flags) {}
	private static final class Hits {
		volatile int size;
		long[] words;
	}

	public long[] cachedWords(String token, IngredientSearchSnapshot snapshot, int last) {
		var hits = queries.getIfPresent(new Query(token, snapshot.searchRevision, snapshot.nativeOptions()));
		return hits != null && hits.size > last - firstId ? hits.words : null;
	}

	public long[] words(String token, IngredientSearchSnapshot snapshot, int last, Runnable checkpoint) {
		var key = new Query(token, snapshot.searchRevision, snapshot.nativeOptions());
		var hits = queries.asMap().computeIfAbsent(key, ignored -> new Hits());
		int needed = last - firstId + 1;
		synchronized (hits) {
			if (hits.size >= needed)
				return hits.words;
			int size;
			Block[] available;
			synchronized (this) {
				size = registered.size();
				available = blocks.toArray(Block[]::new);
			}
			var regular = snapshot.getMaterialBits(token);
			long[] words = hits.words == null ? Arrays.copyOf(regular, (firstId + size + 63) >>> 6)
				: Arrays.copyOf(hits.words, (firstId + size + 63) >>> 6);
			int blockStart = hits.size / BLOCK;
			IntConsumer fillBlock = i -> {
				checkpoint.run();
				int count = Math.min(BLOCK, size - i * BLOCK);
				int offset = (firstId + i * BLOCK) >>> 6;
				var local = available[i].words(snapshot, token, count);
				System.arraycopy(local, 0, words, offset, (count + 63) >>> 6);
			};
			if (available.length - blockStart == 1)
				fillBlock.accept(blockStart);
			else {
				// Other workers in the recipe search pool may already be waiting for these same hits.
				ForkJoinPool.commonPool().submit(() -> IntStream.range(blockStart, available.length).parallel().forEach(fillBlock)).join();
			}
			checkpoint.run();
			hits.words = words;
			hits.size = size;
			return words;
		}
	}

	private final class Block {
		final Entry[] entries = new Entry[BLOCK];
		final Map<PrefixInfo<IListElementInfo<?>, IListElement<?>>, CompactFieldIndex> fields = new IdentityHashMap<>();
		long revision = -1;
		synchronized long[] words(IngredientSearchSnapshot snapshot, String token, int count) {
			if (count < BLOCK) {
				long[] words = new long[BLOCK / 64];
				for (int i = 0; i < count; i++)
					if (entries[i].matches(snapshot, token))
						words[i >>> 6] |= 1L << i;
				return words;
			}
			if (revision != snapshot.searchRevision) {
				fields.clear();
				lastMatch = null;
				revision = snapshot.searchRevision;
			}
			var term = snapshot.term(token);
			var builders = new IdentityHashMap<PrefixInfo<IListElementInfo<?>, IListElement<?>>, CompactFieldIndex.Builder>();
			for (var field : term.fields())
				if (!fields.containsKey(field))
					builders.put(field, new CompactFieldIndex.Builder());
			if (!builders.isEmpty()) {
				for (int i = 0; i < BLOCK; i++)
					entries[i].indexFields(snapshot, builders, i);
				builders.forEach((field, builder) -> fields.put(field, builder.build()));
				lastMatch = null;
				if (entries[0].block == null)
					for (var entry : entries)
						entry.releaseTexts(this);
			}
			long[] words = indexedMatches(snapshot, token).words;
			// An append can promote a former orphan to a registered ingredient with aliases of its own.
			if (snapshot.identityRevision != base.identityRevision) {
				words = words.clone();
				for (int i = 0; i < BLOCK; i++) {
					int regular = snapshot.elementId(entries[i].key);
					if (regular >= 0) {
						long mask = 1L << i;
						if (hit(term.hits(), regular))
							words[i >>> 6] |= mask;
						else
							words[i >>> 6] &= ~mask;
					}
				}
			}
			return words;
		}
		private record FieldMatches(String token, long revision, int flags, long[] words,
			List<PrefixInfo<IListElementInfo<?>, IListElement<?>>> missing) {
			boolean applies(IngredientSearchSnapshot snapshot, String query) {
				return revision == snapshot.searchRevision && flags == snapshot.nativeOptions() && token.equals(query);
			}
		}
		private volatile @Nullable FieldMatches lastMatch;

		private synchronized FieldMatches indexedMatches(IngredientSearchSnapshot snapshot, String token) {
			var current = lastMatch;
			if (current != null && current.applies(snapshot, token))
				return current;
			var term = snapshot.term(token);
			long[] words = new long[BLOCK / 64];
			var missing = new ArrayList<PrefixInfo<IListElementInfo<?>, IListElement<?>>>();
			for (var field : term.fields()) {
				var index = revision == snapshot.searchRevision ? fields.get(field) : null;
				if (index == null)
					missing.add(field);
				else
					index.orMatches(term.token(), words, new CompactFieldIndex.Session(), term.pinyin());
			}
			return lastMatch = new FieldMatches(token, snapshot.searchRevision, snapshot.nativeOptions(), words, List.copyOf(missing));
		}

		boolean matches(Entry entry, IngredientSearchSnapshot snapshot, String token) {
			var current = lastMatch;
			if (current == null || !current.applies(snapshot, token))
				current = indexedMatches(snapshot, token);
			if (hit(current.words, (entry.id - firstId) % BLOCK))
				return true;
			if (current.missing.isEmpty())
				return false;
			var term = snapshot.term(token);
			var info = snapshot.describe(entry.ingredient);
			for (var field : current.missing)
				if (Entry.matchesText(Entry.strings(info, field), term))
					return true;
			return false;
		}
	}

	private static boolean hit(long[] words, int id) {
		return id >>> 6 < words.length && (words[id >>> 6] & 1L << id) != 0;
	}

	static final class Entry {
		final IngredientKey key;
		final ITypedIngredient<?> ingredient;
		final int id;
		long revision = -1;
		@Nullable
		IListElementInfo<?> info;
		@Nullable
		Map<PrefixInfo<IListElementInfo<?>, IListElement<?>>, List<String>> texts;
		volatile @Nullable Block block;
		Entry(IngredientKey key, ITypedIngredient<?> ingredient, int id) { this.key = key; this.ingredient = ingredient; this.id = id; }

		@Override
		public boolean equals(Object other) { return other instanceof Entry entry && key.equals(entry.key); }

		@Override
		public int hashCode() { return key.hashCode(); }

		private static List<String> strings(@Nullable IListElementInfo<?> info, PrefixInfo<IListElementInfo<?>, IListElement<?>> field) {
			return info == null ? List.of() : field.getStrings(info).stream().map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
		}

		synchronized void indexFields(IngredientSearchSnapshot snapshot,
			Map<PrefixInfo<IListElementInfo<?>, IListElement<?>>, CompactFieldIndex.Builder> builders, int row) {
			boolean cached = revision == snapshot.searchRevision && texts != null;
			var description = cached ? info : snapshot.describe(ingredient);
			builders.forEach((field, builder) -> {
				var values = cached ? texts.get(field) : null;
				for (String text : values == null ? strings(description, field) : values)
					builder.put(text, row);
			});
		}

		synchronized void releaseTexts(Block owner) {
			info = null;
			texts = null;
			block = owner;
		}

		private static boolean matchesText(List<String> texts, IngredientSearchSnapshot.TermMatches term) {
			for (String text : texts)
				if (text.contains(term.token()) || term.pinyin() != null && term.pinyin().matches(text))
					return true;
			return false;
		}

		boolean matches(IngredientSearchSnapshot snapshot, String token) {
			var term = snapshot.term(token);
			int regular = snapshot.elementId(key);
			if (regular >= 0)
				return hit(term.hits(), regular);
			if (term.fields().isEmpty())
				return false;
			Block owner = block;
			if (owner == null) {
				synchronized (this) {
					owner = block;
					if (owner == null) {
						if (revision != snapshot.searchRevision || texts == null) {
							info = snapshot.describe(ingredient);
							texts = new IdentityHashMap<>();
							revision = snapshot.searchRevision;
						}
						for (var field : term.fields())
							if (matchesText(texts.computeIfAbsent(field, f -> strings(info, f)), term))
								return true;
						return false;
					}
				}
			}
			return owner.matches(this, snapshot, token);
		}
	}
}

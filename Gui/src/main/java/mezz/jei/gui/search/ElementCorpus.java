package mezz.jei.gui.search;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.SearchMode;
import mezz.jei.common.search.PrefixInfo;
import mezz.jei.common.search.compact.CompactFieldIndex;
import mezz.jei.common.search.compact.PinyinQuery;
import mezz.jei.common.search.compact.PinyinOption;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Owned by the client thread, including queries and their incremental sessions.
 * Workers consume published recipe snapshots or warm immutable field indexes.
 */
public final class ElementCorpus implements IElementSearch {
	private final ElementPrefixParser parser;
	private final java.util.function.IntSupplier options;
	private long[] visible;
	private final List<IListElementInfo<?>> infos;
	private final Reference2IntOpenHashMap<IListElement<?>> ids;
	private Object2IntOpenHashMap<IngredientKey> ingredientIds;
	private boolean publishedIds;
	private final Map<PrefixInfo<IListElementInfo<?>, IListElement<?>>, Field> fields = new IdentityHashMap<>();
	private final List<IListElement<?>> elements;
	private @Nullable List<IListElement<?>> ordering;
	private int[] sortRanks = new int[0];

	record IngredientKey(IIngredientType<?> type, Object uid) {
		static <T> IngredientKey of(ITypedIngredient<T> ingredient, IIngredientManager manager) {
			return new IngredientKey(ingredient.getType(), manager.getIngredientHelper(ingredient.getType())
				.getUid(ingredient.getIngredient(), UidContext.Ingredient));
		}
	}

	public ElementCorpus(ElementPrefixParser parser, Collection<IListElementInfo<?>> initial, IIngredientManager manager, java.util.function.IntSupplier options) {
		this.options = options;
		this.parser = parser;
		visible = new long[(initial.size() + 63) >>> 6];
		infos = new ArrayList<>(initial.size());
		ids = new Reference2IntOpenHashMap<>(initial.size());
		ids.defaultReturnValue(-1);
		ingredientIds = new Object2IntOpenHashMap<>(initial.size());
		ingredientIds.defaultReturnValue(-1);
		for (var info : initial) {
			register(info, manager);
		}
		elements = Collections.unmodifiableList(Lists.transform(infos, IListElementInfo::getElement));
		rebuildSearchIndexes(EnumSet.allOf(ElementSearchIndex.class), infos);
	}

	private int register(IListElementInfo<?> info, IIngredientManager manager) {
		if (publishedIds) {
			ingredientIds = ingredientIds.clone();
			publishedIds = false;
		}
		var key = IngredientKey.of(info.getTypedIngredient(), manager);
		int id = ingredientIds.putIfAbsent(key, infos.size());
		if (id < 0) {
			id = infos.size();
			infos.add(info);
		} else {
			ids.removeInt(infos.set(id, info).getElement());
		}
		ids.put(info.getElement(), id);
		if ((id >>> 6) >= visible.length) {
			visible = Arrays.copyOf(visible, Math.max((id >>> 6) + 1, 2 * visible.length));
		}
		setVisible(id, info.getElement().isVisible());
		return id;
	}

	private void setVisible(int id, boolean show) {
		if (show) {
			visible[id >>> 6] |= 1L << id;
		} else {
			visible[id >>> 6] &= ~(1L << id);
		}
	}

	public void updateVisibility(IListElement<?> element) {
		int id = ids.getInt(element);
		if (id >= 0) {
			setVisible(id, element.isVisible());
		}
	}

	Object2IntOpenHashMap<IngredientKey> snapshotIngredientIds() {
		// Recipe workers keep this generation while the client can continue registering ingredients.
		publishedIds = true;
		return ingredientIds;
	}

	@Override
	public Collection<IListElement<?>> getAllIngredients() {
		return elements;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> @Nullable IListElement<T> findElement(ITypedIngredient<T> ingredient, IIngredientHelper<T> helper) {
		int id = ingredientIds.getInt(new IngredientKey(ingredient.getType(), helper.getUid(ingredient.getIngredient(), UidContext.Ingredient)));
		return id < 0 ? null : (IListElement<T>) elements.get(id);
	}

	@Override
	public <T> void add(IListElementInfo<T> info, IIngredientManager manager) {
		int id = register(info, manager);
		var rebuild = EnumSet.noneOf(ElementSearchIndex.class);
		for (var index : ElementSearchIndex.values()) {
			replace(index, id, info, rebuild);
		}
		rebuildSearchIndexes(rebuild, infos);
	}

	@Override
	public void update(Collection<IListElementInfo<?>> updated, Set<ElementSearchIndex> rebuilding, Collection<IListElementInfo<?>> allInfos) {
		var rebuild = EnumSet.noneOf(ElementSearchIndex.class);
		rebuild.addAll(rebuilding);
		for (var info : updated) {
			int id = ids.getInt(info.getElement());
			if (id < 0) {
				// A newer element with the same typed UID superseded this queued update.
				continue;
			}
			infos.set(id, info);
			for (var index : ElementSearchIndex.values()) {
				replace(index, id, info, rebuild);
			}
		}
		rebuildSearchIndexes(rebuild, allInfos);
	}

	private void replace(ElementSearchIndex index, int id, IListElementInfo<?> info, Set<ElementSearchIndex> rebuild) {
		var prefix = parser.getPrefixInfo(index);
		if (rebuild.contains(index) || prefix.getMode() == SearchMode.DISABLED) {
			return;
		}
		var field = fields.get(prefix);
		List<String> keys = prefix.getStrings(info).stream().map(String::trim).filter(key -> !key.isEmpty()).toList();
		var previous = field.changes.put(id, keys);
		field.changedAssociations += keys.size() - (previous == null ? 0 : previous.size());
		if (field.changes.size() > Math.max(256, infos.size() / 8) || field.changedAssociations > Math.max(256, field.base.associations() / 32)) {
			rebuild.add(index);
		}
	}

	@Override
	public void rebuildSearchIndexes(Set<ElementSearchIndex> indexes, Collection<IListElementInfo<?>> allInfos) {
		if (indexes.isEmpty()) {
			return;
		}
		if (allInfos != infos) {
			for (var info : allInfos) {
				int id = ids.getInt(info.getElement());
				if (id >= 0) {
					infos.set(id, info);
				}
			}
		}
		var pending = new EnumMap<ElementSearchIndex, CompletableFuture<CompactFieldIndex>>(ElementSearchIndex.class);
		for (var index : indexes) {
			var prefix = parser.getPrefixInfo(index);
			var builder = new CompactFieldIndex.Builder(index == ElementSearchIndex.NAMES_AND_ALIASES ? infos.size() : 16);
			if (prefix.getMode() != SearchMode.DISABLED) {
				for (int id = 0; id < infos.size(); id++) {
					var info = infos.get(id);
					for (var string : prefix.getStrings(info)) {
						String key = string.trim();
						if (!key.isEmpty()) {
							builder.put(key, id);
						}
					}
				}
			}
			pending.put(index, builder.buildAsync(ForkJoinPool.commonPool()));
		}
		CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new)).join();
		int flags = options.getAsInt();
		pending.forEach((index, future) -> {
			var prefix = parser.getPrefixInfo(index);
			var base = future.join();
			fields.put(prefix, new Field(base));
			schedulePinyinWarmup(prefix, base, flags);
		});
	}

	public void warmPinyin() {
		int flags = options.getAsInt();
		fields.forEach((prefix, field) -> schedulePinyinWarmup(prefix, field.base, flags));
	}

	private static void schedulePinyinWarmup(PrefixInfo<?, ?> prefix, CompactFieldIndex base, int flags) {
		if ((flags & PinyinOption.ENABLED.bit) != 0 && prefix.getMode() != SearchMode.DISABLED) {
			ForkJoinPool.commonPool().execute(() -> base.warmPinyin(flags));
		}
	}

	private static final class Field {
		final CompactFieldIndex base;
		final CompactFieldIndex.Session session = new CompactFieldIndex.Session();
		final Int2ObjectOpenHashMap<List<String>> changes = new Int2ObjectOpenHashMap<>();
		int changedAssociations;

		Field(CompactFieldIndex base) {
			this.base = base;
		}

		void union(String token, long[] result, PinyinQuery query) {
			if (changes.isEmpty()) {
				base.orMatches(token, result, session, query);
				return;
			}
			long[] local = new long[result.length];
			base.orMatches(token, local, session, query);
			for (var entry : changes.int2ObjectEntrySet()) {
				int id = entry.getIntKey();
				local[id >>> 6] &= ~(1L << id);
				for (String key : entry.getValue()) {
					if (key.contains(token) || query != null && query.matches(key)) {
						local[id >>> 6] |= 1L << id;
						break;
					}
				}
			}
			for (int i = 0; i < result.length; i++) {
				result[i] |= local[i];
			}
		}
	}

	public long[] emptyMatches() {
		return new long[(infos.size() + 63) >>> 6];
	}

	private void union(ElementPrefixParser.TokenInfo token, long[] result) {
		if (token.token().isEmpty()) {
			return;
		}
		var query = PinyinQuery.compile(token.token(), options.getAsInt());
		var prefix = token.prefixInfo();
		var field = fields.get(prefix);
		if (prefix != parser.getNoPrefix() && field != null && prefix.getMode() != SearchMode.DISABLED) {
			field.union(token.token(), result, query);
		} else {
			fields.forEach((candidate, index) -> {
				if (candidate.getMode() == SearchMode.ENABLED) {
					index.union(token.token(), result, query);
				}
			});
		}
	}

	BitSet getMatchBits(ElementPrefixParser.TokenInfo token) {
		return BitSet.valueOf(getMatchWords(token));
	}

	long[] getMatchWords(ElementPrefixParser.TokenInfo token) {
		long[] result = emptyMatches();
		union(token, result);
		return result;
	}

	public void unionGroup(List<ElementPrefixParser.TokenInfo> required, List<ElementPrefixParser.TokenInfo> excluded, long[] output) {
		if (required.size() == 1 && excluded.isEmpty()) {
			union(required.getFirst(), output);
			return;
		}
		long[] group = emptyMatches();
		long[] term = emptyMatches();
		for (int i = 0; i < required.size(); i++) {
			if (i == 0) {
				union(required.get(i), group);
			} else {
				Arrays.fill(term, 0);
				union(required.get(i), term);
				for (int j = 0; j < group.length; j++) {
					group[j] &= term[j];
				}
			}
		}
		if (!excluded.isEmpty() && Arrays.stream(group).noneMatch(word -> word != 0)) {
			Arrays.fill(group, -1L);
			if (group.length != 0) {
				group[group.length - 1] &= -1L >>> (group.length * Long.SIZE - infos.size());
			}
		}
		Arrays.fill(term, 0);
		for (var token : excluded) {
			union(token, term);
		}
		for (int j = 0; j < group.length; j++) {
			output[j] |= group[j] & ~term[j];
		}
	}

	public Stream<IListElement<?>> visibleMatches(List<IListElement<?>> sorted, long[] matches) {
		if (ordering != sorted) {
			sortRanks = new int[infos.size()];
			for (int i = 0; i < sorted.size(); i++) {
				sortRanks[ids.getInt(sorted.get(i))] = i;
			}
			ordering = sorted;
		}
		var ranked = new BitSet(sorted.size());
		for (int i = 0; i < matches.length; i++) {
			long word = matches[i] & visible[i];
			while (word != 0) {
				int id = i * Long.SIZE + Long.numberOfTrailingZeros(word);
				ranked.set(sortRanks[id]);
				word &= word - 1;
			}
		}
		return ranked.stream().mapToObj(sorted::get);
	}

	@Override
	public Set<IListElement<?>> getSearchResults(ElementPrefixParser.TokenInfo token) {
		Set<IListElement<?>> result = Collections.newSetFromMap(new IdentityHashMap<>());
		collectSearchResults(token, result::add);
		return result;
	}

	@Override
	public void collectSearchResults(ElementPrefixParser.TokenInfo token, Consumer<IListElement<?>> consumer) {
		var hits = getMatchBits(token);
		hits.stream().forEach(id -> consumer.accept(elements.get(id)));
	}

	@Override
	public void logStatistics() {
		fields.forEach((prefix, field) -> {
			if (prefix.getMode() != SearchMode.DISABLED) {
				ElementSearch.logStatistics(prefix, "associations=" + field.base.associations() + ", pending=" + field.changedAssociations);
			}
		});
	}
}

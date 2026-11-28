package mezz.jei.gui.search;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.common.ingredients.IItemUidHelperInternal;
import mezz.jei.common.ingredients.itemStacks.TypedItemStack;
import mezz.jei.gui.search.ElementCorpus.IngredientKey;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.search.compact.CompactSearchStorageBuilder;
import mezz.jei.common.search.compact.PinyinQuery;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.search.ISearchStorageBuilderFactory;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.common.config.SearchMode;
import mezz.jei.common.search.PrefixInfo;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.ingredients.ListElementInfo;
import mezz.jei.gui.ingredients.ListElement;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMaps;
import org.jetbrains.annotations.Nullable;

/** Query-local material results, captured by the index owner and read by recipe workers. */
public final class IngredientSearchSnapshot implements BiPredicate<ITypedIngredient<?>, String> {
	private static final long MAX_MATCH_BYTES = 8L * 1024 * 1024;
	private final @Nullable IngredientIdentities identities;
	private final IElementSearch search;
	final long identityRevision;
	final long searchRevision;
	final int nativeOptions() { return nativeFlags; }
	private @Nullable OrphanIngredientCorpus recipeCorpus;

	public void useRecipeCorpus(OrphanIngredientCorpus corpus) { recipeCorpus = corpus; }

	int elementCount() { return identities.ingredientIds.size(); }
	int elementId(IngredientKey key) { return identities.ingredientIds.getInt(key); }
	TermMatches term(String token) { return terms.get(token); }

	IListElementInfo<?> describe(ITypedIngredient<?> ingredient) {
		return ListElementInfo.createFromElement(new ListElement<>(ingredient, 0), ingredientManager, config, modIdHelper);
	}
	private final Map<String, TermMatches> terms = new HashMap<>();
	private final Cache<CandidateTerm, Boolean> extraMatches = CacheBuilder.newBuilder().maximumSize(8192).build();
	private final IIngredientManager ingredientManager;
	private final IIngredientFilterConfig config;
	private final IModIdHelper modIdHelper;
	private final int nativeFlags;
	private final ISearchStorageBuilderFactory storageFactory;

	public IngredientSearchSnapshot(Set<String> tokens, IElementSearch search, ElementPrefixParser parser,
		IIngredientManager ingredientManager, IIngredientFilterConfig config, IModIdHelper modIdHelper, ISearchStorageBuilderFactory storageFactory,
		long identityRevision, long searchRevision, @Nullable IngredientSearchSnapshot previous) {
		this.ingredientManager = ingredientManager;
		this.config = config;
		this.modIdHelper = modIdHelper;
		this.storageFactory = storageFactory;
		this.nativeFlags = storageFactory instanceof CompactSearchStorageBuilder.Factory factory ? factory.options().getAsInt() : 0;
		this.search = search;
		this.identityRevision = identityRevision;
		this.searchRevision = searchRevision;
		boolean sameIdentities = previous != null && previous.search == search && previous.identityRevision == identityRevision;
		IngredientIdentities identities = sameIdentities ? previous.identities : null;
		if (identities == null && search instanceof ElementCorpus corpus) {
			identities = new IngredientIdentities(corpus.snapshotIngredientIds(), Reference2IntMaps.emptyMap());
		} else if (identities == null && !tokens.isEmpty()) {
			var elements = search.getAllIngredients();
			identities = new IngredientIdentities(elements.size());
			for (IListElement<?> element : elements) {
				int id = identities.elementIds().size();
				identities.elementIds().put(element, id);
				identities.ingredientIds().put(getKey(element.getTypedIngredient()), id);
			}
		}
		this.identities = identities;
		if (tokens.isEmpty())
			return;
		var elementIds = identities.elementIds();
		int elementCount = search instanceof ElementCorpus ? search.getAllIngredients().size() : elementIds.size();
		long bytesPerTerm = ((elementCount + 63L) / 64) * Long.BYTES;
		long remaining = MAX_MATCH_BYTES;
		if (sameIdentities && previous.nativeFlags == nativeFlags && previous.searchRevision == searchRevision) {
			// Only carry forward conditions still used by this query, never a search-history cache.
			for (String token : tokens) {
				TermMatches cached = previous.terms.get(token);
				if (cached != null && cached.indexed()) {
					terms.put(token, cached);
					remaining -= (long) cached.hits().length * Long.BYTES;
				}
			}
		}
		for (String token : tokens) {
			if (terms.containsKey(token))
				continue;
			var parsed = parser.parseToken(token);
			if (parsed.isEmpty()) {
				terms.put(token, new TermMatches("", List.of(), new long[0], true, null));
				continue;
			}
			var info = parsed.get();
			List<PrefixInfo<IListElementInfo<?>, IListElement<?>>> fields = info.prefixInfo() == parser.getNoPrefix()
				? parser.allPrefixInfos().stream().filter(field -> field.getMode() == SearchMode.ENABLED).toList()
				: List.of(info.prefixInfo());
			boolean indexed = bytesPerTerm <= remaining;
			long[] hits;
			if (indexed && search instanceof ElementCorpus corpus) {
				hits = corpus.getMatchWords(info);
				remaining -= bytesPerTerm;
			} else if (indexed) {
				var bits = new BitSet(elementCount);
				search.collectSearchResults(info, element -> {
					int id = elementIds.getInt(element);
					if (id >= 0)
						bits.set(id);
				});
				hits = bits.toLongArray();
				remaining -= bytesPerTerm;
			} else {
				hits = new long[0];
			}
			terms.put(token, new TermMatches(info.token(), fields, hits, indexed, PinyinQuery.compile(info.token(), nativeFlags)));
		}
	}

	/** Read-only words shared with recipe workers; null means this term requires on-demand matching. */
	public long @Nullable [] getMaterialBits(String token) {
		var term = terms.get(token);
		return term != null && term.indexed ? term.hits : null;
	}

	public boolean hasNativeIds() {
		return search instanceof ElementCorpus;
	}

	@Override
	public boolean test(ITypedIngredient<?> ingredient, String token) {
		TermMatches term = terms.get(token);
		IngredientKey key = getKey(ingredient);
		int id = identities.ingredientIds().getInt(key);
		if (id >= 0 && term.indexed())
			return (id >>> 6) < term.hits.length && (term.hits[id >>> 6] & (1L << id)) != 0;
		if (recipeCorpus != null && term.indexed())
			return recipeCorpus.testExtra(key, ingredient, token, this);
		CandidateTerm candidate = new CandidateTerm(key, token);
		Boolean cached = extraMatches.getIfPresent(candidate);
		if (cached != null)
			return cached;
		boolean matched = matchesExtra(ingredient, term);
		extraMatches.put(candidate, matched);
		return matched;
	}

	private boolean matchesExtra(ITypedIngredient<?> ingredient, TermMatches term) {
		if (term.fields().isEmpty())
			return false;
		IListElementInfo<?> info = ListElementInfo.createFromElement(new ListElement<>(ingredient, 0), ingredientManager, config, modIdHelper);
		if (info == null)
			return false;
		for (var field : term.fields()) {
			var strings = field.getStrings(info);
			if (storageFactory instanceof CompactSearchStorageBuilder.Factory) {
				for (String string : strings) {
					String text = string.trim();
					if (!text.isEmpty() && (text.contains(term.token()) || term.pinyin() != null && term.pinyin().matches(text)))
						return true;
				}
			} else {
				// Third-party factories may register their builders in shared mutable registries.
				synchronized (storageFactory) {
					var builder = field.createStorageBuilder();
					for (String string : strings)
						ElementSearch.putIfNotBlank(builder, string, info.getElement());
					var storage = builder.build();
					boolean[] matched = {false};
					storage.getSearchResults(term.token(), values -> matched[0] |= values.contains(info.getElement()));
					if (matched[0])
						return true;
				}
			}
		}
		return false;
	}

	boolean canUseItemAsUid(TypedItemStack stack) {
		return ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK) instanceof IItemUidHelperInternal helper &&
			helper.canUseItemAsUid(stack.getIngredient());
	}

	<T> IngredientKey getKey(ITypedIngredient<T> ingredient) {
		return IngredientKey.of(ingredient, ingredientManager);
	}

	private record IngredientIdentities(Object2IntOpenHashMap<IngredientKey> ingredientIds, Reference2IntMap<IListElement<?>> elementIds) {
		private IngredientIdentities(int size) {
			this(new Object2IntOpenHashMap<>(size), new Reference2IntOpenHashMap<>(size));
			ingredientIds.defaultReturnValue(-1);
			elementIds.defaultReturnValue(-1);
		}
	}
	private record CandidateTerm(IngredientKey ingredient, String token) {}
	record TermMatches(String token, List<PrefixInfo<IListElementInfo<?>, IListElement<?>>> fields, long[] hits, boolean indexed, @Nullable PinyinQuery pinyin) {}
}

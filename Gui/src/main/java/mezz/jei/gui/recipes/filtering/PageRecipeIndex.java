package mezz.jei.gui.recipes.filtering;

import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.Arrays;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;
import mezz.jei.gui.search.IngredientSearchSnapshot;
import java.util.AbstractList;
import java.util.BitSet;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/** Only the visible order and dynamic holes belong to a page. Material and path data belong to the category. */
final class PageRecipeIndex<T> {
	private final CompactRecipeIndex<T> index;
	private final List<T> recipes;
	private final int[] ids;
	// A complete registered sequence may have plugin rows interleaved between its static rows.
	private final int[] pageById;
	private final BitSet dynamic = new BitSet();

	PageRecipeIndex(CompactRecipeIndex<T> index, List<T> recipes) {
		this.index = index;
		this.recipes = recipes;
		boolean entire = recipes == index.recipes;
		if (!entire && recipes.size() == index.recipes.size()) {
			entire = true;
			for (int i = 0; i < recipes.size(); i++)
				if (recipes.get(i) != index.recipes.get(i)) {
					entire = false;
					break;
				}
		}
		ids = entire ? null : new int[recipes.size()];
		boolean ordered = ids != null && recipes.size() >= index.recipes.size() && recipes.size() > 2048;
		int staticCount = 0;
		if (ids != null) {
			for (int i = 0; i < ids.length; i++) {
				int id = ids[i] = index.id(recipes.get(i));
				if (id < 0)
					dynamic.set(i);
				else if (id != staticCount++)
					ordered = false;
			}
		}
		pageById = ordered && staticCount == index.recipes.size() ? new int[staticCount] : null;
		if (pageById != null) {
			for (int row = 0; row < ids.length; row++)
				if (ids[row] >= 0)
					pageById[ids[row]] = row;
		}
	}

	boolean hasDynamic() { return !dynamic.isEmpty(); }
	boolean canRunInline(RecipeSearchQuery query, IngredientSearchSnapshot snapshot, int size) {
		return dynamic.cardinality() <= 512 && index.canRunInline(query, snapshot, size);
	}
	boolean ownsRows(List<?> source) {
		return source == recipes || source.isEmpty() || source instanceof Rows<?> rows && rows.source == recipes;
	}

	private BitSet candidates(List<?> source) {
		var bits = new BitSet();
		if (source == recipes)
			bits.set(0, recipes.size());
		else if (source instanceof Rows<?> rows && rows.source == recipes) {
			for (int row : rows.ids)
				bits.set(row);
		} else if (!source.isEmpty())
			return null;
		return bits;
	}

	IFocusedRecipes<T> includeDynamic(List<?> previous) {
		var bits = candidates(previous);
		if (bits == null)
			return new StaticFocusedRecipes<>(index.category, recipes);
		bits.or(dynamic);
		return new StaticFocusedRecipes<>(index.category, project(bits));
	}

	private List<T> project(BitSet hits) {
		if (hits.isEmpty())
			return List.of();
		if (hits.cardinality() == recipes.size())
			return recipes;
		return new Rows<>(recipes, hits.stream().toArray());
	}

	Selection select(List<?> source, int skip, int limit, RecipeSearchQuery query, IngredientSearchSnapshot snapshot, boolean exact, Runnable checkpoint) {
		if (pageById != null && source == recipes && !exact) {
			int end = limit < 0 ? recipes.size() : skip + limit;
			var candidates = new BitSet();
			int first = dynamic.nextClearBit(skip);
			if (first < end) {
				int last = dynamic.previousClearBit(end - 1);
				candidates.set(ids[first], ids[last] + 1);
			}
			var hits = index.match(candidates, query, snapshot, checkpoint);
			if (hits == null)
				return null;
			var found = new BitSet();
			for (int id = hits.nextSetBit(0); id >= 0; id = hits.nextSetBit(id + 1))
				found.set(pageById[id]);
			var pending = (BitSet) dynamic.clone();
			pending.clear(0, skip);
			if (end < pending.length())
				pending.clear(end, pending.length());
			return new Selection(found, pending);
		}
		var visible = candidates(source);
		if (visible == null)
			return null;
		for (int i = visible.nextSetBit(0); skip > 0 && i >= 0; i = visible.nextSetBit(i + 1), skip--)
			visible.clear(i);
		if (limit >= 0) {
			int end = visible.nextSetBit(0);
			for (int i = 0; end >= 0 && i < limit; i++)
				end = visible.nextSetBit(end + 1);
			if (end >= 0)
				visible.clear(end, visible.length());
		}
		var pending = (BitSet) visible.clone();
		pending.and(dynamic);
		visible.andNot(dynamic);
		if (exact)
			return new Selection(visible, pending);
		BitSet candidates = visible;
		if (ids != null) {
			candidates = new BitSet();
			for (int row = visible.nextSetBit(0); row >= 0; row = visible.nextSetBit(row + 1))
				candidates.set(ids[row]);
		}
		var hits = index.match(candidates, query, snapshot, checkpoint);
		if (hits == null)
			return null;
		if (ids == null)
			return new Selection(hits, pending);
		for (int row = visible.nextSetBit(0); row >= 0; row = visible.nextSetBit(row + 1))
			if (!hits.get(ids[row]))
				visible.clear(row);
		return new Selection(visible, pending);
	}

	final class Selection {
		private final BitSet found;
		private final int[] pending;
		private int next;

		Selection(BitSet found, BitSet pending) {
			this.found = found;
			this.pending = pending.stream().toArray();
		}

		List<T> finishInline(Function<T, Boolean> test) {
			while (next < pending.length) {
				int row = pending[next];
				var matched = test.apply(recipes.get(row));
				if (matched == null)
					return null;
				if (matched)
					found.set(row);
				next++;
			}
			return project(found);
		}

		List<T> finish(Predicate<T> test, Runnable checkpoint, Consumer<Supplier<List<T>>> publish, Runnable changed) {
			final int batchSize = RecipeSearchTraversal.RECIPES_PER_BATCH;
			int blocks = (pending.length - next + batchSize - 1) / batchSize;
			if (blocks == 0)
				return project(found);
			var chunks = new AtomicReferenceArray<BitSet>(blocks);
			Supplier<List<T>> prefix = () -> {
				var hits = (BitSet) found.clone();
				for (int b = 0; b < blocks; b++) {
					int start = next + b * batchSize;
					var chunk = chunks.get(b);
					if (chunk == null) {
						hits.clear(pending[start], recipes.size());
						break;
					}
					for (int j = chunk.nextSetBit(0); j >= 0; j = chunk.nextSetBit(j + 1))
						hits.set(pending[start + j]);
				}
				return project(hits);
			};
			publish.accept(prefix);
			var nextBlock = new java.util.concurrent.atomic.AtomicInteger();
			IntStream.range(0, blocks).parallel().forEach(ignored -> {
				// Earlier rows can be published as soon as their contiguous prefix finishes.
				int b = nextBlock.getAndIncrement();
				var chunk = new BitSet(batchSize);
				int start = next + b * batchSize;
				for (int i = start; i < Math.min(start + batchSize, pending.length); i++) {
					checkpoint.run();
					if (test.test(recipes.get(pending[i])))
						chunk.set(i - start);
				}
				chunks.set(b, chunk);
				changed.run();
			});
			return prefix.get();
		}
	}

	static List<?> joinRows(List<?> first, List<?> second) {
		if (first instanceof Rows<?> a && second instanceof Rows<?> b && a.source == b.source) {
			int[] joined = Arrays.copyOf(a.ids, a.size() + b.size());
			System.arraycopy(b.ids, 0, joined, a.size(), b.size());
			return new Rows<>(a.source, joined);
		}
		return null;
	}

	private static final class Rows<T> extends AbstractList<T> implements RandomAccess {
		private final List<T> source;
		private final int[] ids;
		Rows(List<T> source, int[] ids) { this.source = source; this.ids = ids; }
		@Override
		public T get(int i) { return source.get(ids[i]); }
		@Override
		public int size() { return ids.length; }
	}
}

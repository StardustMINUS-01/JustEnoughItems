package mezz.jei.common.search.compact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import mezz.jei.api.search.ISearchStorage;
import mezz.jei.api.search.ISearchStorageBuilder;
import mezz.jei.api.search.ISearchStorageBuilderFactory;

/** Adapts object identities to dense ids for callers of the search storage API. */
public class CompactSearchStorageBuilder<T> implements ISearchStorageBuilder<T> {
	public static final Factory FACTORY = new Factory(() -> 0);

	public record Factory(java.util.function.IntSupplier options) implements ISearchStorageBuilderFactory {
		@Override
		public <T> ISearchStorageBuilder<T> create() {
			return new CompactSearchStorageBuilder<>(options);
		}

		@Override
		public boolean supportsDeletion() {
			return true;
		}
	}

	private final java.util.function.IntSupplier options;
	private CompactFieldIndex.Builder field = new CompactFieldIndex.Builder();
	private final List<T> values = new ArrayList<>();
	private final Reference2IntOpenHashMap<T> ids = new Reference2IntOpenHashMap<>();

	public CompactSearchStorageBuilder() {
		this(() -> 0);
	}

	private CompactSearchStorageBuilder(java.util.function.IntSupplier options) {
		this.options = options;
		ids.defaultReturnValue(-1);
	}

	@Override
	public void put(String key, T value) {
		checkNotBuilt();
		Objects.requireNonNull(key);
		Objects.requireNonNull(value);
		int id = ids.putIfAbsent(value, values.size());
		if (id == -1) {
			id = values.size();
			values.add(value);
		}
		field.put(key, id);
	}

	@Override
	public ISearchStorage<T> build() {
		checkNotBuilt();
		var storage = new Storage<>(field.build(), values, ids, options);
		field = null;
		return storage;
	}

	private void checkNotBuilt() {
		if (field == null) {
			throw new IllegalStateException("Search storage has already been built");
		}
	}

	private static final class Storage<T> implements ISearchStorage<T> {
		private record Entry(String key, int id) {}
		private final java.util.function.IntSupplier options;
		private CompactFieldIndex base;
		private List<T> values;
		private Reference2IntOpenHashMap<T> ids;
		private final List<Entry> delta = new ArrayList<>();
		private CompactFieldIndex.Session session = new CompactFieldIndex.Session();
		private int removed;
		private int baseElements;

		private Storage(CompactFieldIndex base, List<T> values, Reference2IntOpenHashMap<T> ids, java.util.function.IntSupplier options) {
			this.options = options;
			this.base = base;
			this.values = values;
			this.ids = ids;
			this.baseElements = values.size();
		}

		@Override
		public void getSearchResults(String token, Consumer<Collection<T>> resultsConsumer) {
			List<T> result = collectMatches(token);
			if (!result.isEmpty()) {
				resultsConsumer.accept(result);
			}
		}

		private synchronized List<T> collectMatches(String token) {
			long[] bits = new long[(values.size() + 63) >>> 6];
			var query = PinyinQuery.compile(token, options.getAsInt());
			base.orMatches(token, bits, session, query);
			for (Entry entry : delta) {
				if (entry.key.contains(token) || query != null && query.matches(entry.key)) {
					bits[entry.id >>> 6] |= 1L << entry.id;
				}
			}
			int count = 0;
			for (long word : bits) {
				count += Long.bitCount(word);
			}
			List<T> result = new ArrayList<>(count);
			HybridPostings.forEach(bits, id -> {
				T value = values.get(id);
				if (value != null) {
					result.add(value);
				}
			});
			return result;
		}

		@Override
		public void getAllElements(Consumer<Collection<T>> resultsConsumer) {
			List<T> snapshot;
			synchronized (this) {
				snapshot = List.copyOf(ids.keySet());
			}
			resultsConsumer.accept(snapshot);
		}

		@Override
		public synchronized void put(String key, T value) {
			Objects.requireNonNull(key);
			Objects.requireNonNull(value);
			int id = ids.putIfAbsent(value, values.size());
			if (id == -1) {
				id = values.size();
				values.add(value);
			}
			delta.add(new Entry(key, id));
			compactIfNeeded();
		}

		@Override
		public boolean supportsDeletion() {
			return true;
		}

		@Override
		public synchronized boolean removeAll(Collection<T> removedValues) {
			for (T value : removedValues) {
				int id = ids.removeInt(value);
				if (id >= 0) {
					values.set(id, null);
					removed++;
				}
			}
			compactIfNeeded();
			return true;
		}

		private void compactIfNeeded() {
			if (delta.size() <= Math.max(256, base.associations() / 32) && removed <= baseElements / 8) {
				return;
			}
			var builder = new CompactSearchStorageBuilder<T>();
			base.forEachAssociation((key, id) -> {
				T value = values.get(id);
				if (value != null) {
					builder.put(key, value);
				}
			});
			for (Entry entry : delta) {
				T value = values.get(entry.id);
				if (value != null) {
					builder.put(entry.key, value);
				}
			}
			base = builder.field.build();
			values = builder.values;
			ids = builder.ids;
			delta.clear();
			removed = 0;
			baseElements = values.size();
			session = new CompactFieldIndex.Session();
		}

		@Override
		public synchronized String statistics() {
			return "elements=" + ids.size() + ", base associations=" + base.associations() + ", pending=" + delta.size();
		}
	}
}

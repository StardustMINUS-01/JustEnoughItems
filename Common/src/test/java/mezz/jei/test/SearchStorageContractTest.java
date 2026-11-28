package mezz.jei.test;

import mezz.jei.api.search.ISearchStorage;
import mezz.jei.common.search.LimitedStringStorageBuilder;
import mezz.jei.common.search.compact.CompactSearchStorageBuilder;
import mezz.jei.common.search.LimitedStringStorage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class SearchStorageContractTest {
	@Test
	public void matchesLiteralSubstringsAcrossWordsAndUnicode() {
		CompactSearchStorageBuilder<String> builder = new CompactSearchStorageBuilder<>();
		builder.put("aer crystal", "crystal");
		builder.put("星の結晶 🧪", "unicode");
		builder.put("a*b?c\\d", "punctuation");
		builder.put("unrelated", "other");
		ISearchStorage<String> storage = builder.build();
		Assertions.assertEquals(Set.of("crystal"), search(storage, "er cry"));
		Assertions.assertEquals(Set.of("unicode"), search(storage, "結晶 🧪"));
		for (String literal : new String[]{"*", "?", "\\", "*b?c\\"}) {
			Assertions.assertEquals(Set.of("punctuation"), search(storage, literal));
		}
		Assertions.assertTrue(search(storage, "AER").isEmpty());
		Assertions.assertTrue(search(storage, "missing").isEmpty());
	}

	@Test
	public void returnsAllMatchingValuesWithoutRankingOrTruncation() {
		CompactSearchStorageBuilder<Object> builder = new CompactSearchStorageBuilder<>();
		Set<Object> expected = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int i = 0; i < 1500; i++) {
			Object value = new Object();
			expected.add(value);
			builder.put("shared key " + i, value);
			builder.put("another shared key " + i, value);
		}
		ISearchStorage<Object> storage = builder.build();
		Assertions.assertEquals(expected, search(storage, "shared"));
		Assertions.assertEquals(expected, allElements(storage));
	}

	@Test
	public void preservesValueMappingAcrossRuntimeBatchesAndSegmentMerges() {
		CompactSearchStorageBuilder<String> builder = new CompactSearchStorageBuilder<>();
		builder.put("original", "original value");
		ISearchStorage<String> storage = builder.build();
		for (int batch = 0; batch < 30; batch++) {
			for (int i = 0; i < 20; i++) {
				String key = "entry[" + (batch * 20 + i) + "]";
				storage.put(key, key);
			}
			Assertions.assertEquals(1 + (batch + 1) * 20, allElements(storage).size());
			Assertions.assertEquals(Set.of("original value"), search(storage, "original"));
			Assertions.assertEquals(Set.of("entry[" + batch * 20 + "]"), search(storage, "[" + batch * 20 + "]"));
		}
		for (int i = 0; i < 600; i++) {
			Assertions.assertEquals(Set.of("entry[" + i + "]"), search(storage, "[" + i + "]"));
		}
	}

	@Test
	public void supportsEmptyIndexesAndRuntimeOnlyIngredients() {
		ISearchStorage<String> storage = new CompactSearchStorageBuilder<String>().build();
		Assertions.assertTrue(search(storage, "crystal").isEmpty());
		Assertions.assertTrue(allElements(storage).isEmpty());
		storage.put("aer crystal", "aer");
		storage.put("ignis crystal", "ignis");
		Assertions.assertEquals(Set.of("aer", "ignis"), search(storage, "crystal"));
	}

	@Test
	public void indexesLongStringsAndTheirAliases() {
		String atLimit = "結".repeat(10_922);
		String aboveLimit = atLimit + "x";
		CompactSearchStorageBuilder<String> builder = new CompactSearchStorageBuilder<>();
		builder.put(atLimit, "boundary");
		builder.put(aboveLimit, "long");
		builder.put("valid alias", "long");
		builder.put(aboveLimit, "only oversized");
		ISearchStorage<String> storage = builder.build();
		Assertions.assertEquals(Set.of("boundary", "long", "only oversized"), search(storage, "結"));
		Assertions.assertEquals(Set.of("long", "only oversized"), search(storage, "x"));
		Assertions.assertEquals(Set.of("long", "only oversized"), search(storage, aboveLimit));
		Assertions.assertEquals(Set.of("long"), search(storage, "alias"));
		Assertions.assertEquals(Set.of("boundary", "long", "only oversized"), allElements(storage));
	}

	@Test
	public void indexesLongRuntimeStringsAndKeepsOtherSearchKeys() {
		CompactSearchStorageBuilder<String> builder = new CompactSearchStorageBuilder<>();
		builder.put("original", "original");
		ISearchStorage<String> storage = builder.build();
		storage.put("x".repeat(32_767) + "skipped", "runtime");
		storage.put("runtime alias", "runtime");
		storage.put("another ingredient", "another");
		Assertions.assertEquals(Set.of("runtime"), search(storage, "skipped"));
		Assertions.assertEquals(Set.of("runtime"), search(storage, "alias"));
		Assertions.assertEquals(Set.of("original"), search(storage, "original"));
		Assertions.assertEquals(Set.of("another"), search(storage, "another"));
		Assertions.assertEquals(Set.of("original", "runtime", "another"), allElements(storage));
	}

	@Test
	public void updatesSharedKeysAndIndexesNewKeysInLimitedStringStorage() {
		LimitedStringStorageBuilder<String> builder = new LimitedStringStorageBuilder<>(CompactSearchStorageBuilder::new);
		builder.put("shared", "first");
		ISearchStorage<String> storage = builder.build();
		storage.put("shared", "second");
		storage.put("new key", "third");
		Assertions.assertEquals(Set.of("first", "second"), search(storage, "shared"));
		Assertions.assertEquals(Set.of("third"), search(storage, "new"));
		Assertions.assertEquals(Set.of("first", "second", "third"), allElements(storage));
	}

	@Test
	public void replacesAllKeysWithoutResurrectingRemovedOrPendingEntries() {
		CompactSearchStorageBuilder<Object> builder = new CompactSearchStorageBuilder<>();
		Object crystal = new Object();
		Object other = new Object();
		builder.put("mysterious crystal", crystal);
		builder.put("undiscovered aspect", crystal);
		builder.put("other crystal", other);
		ISearchStorage<Object> storage = builder.build();
		for (int batch = 0; batch < 30; batch++) {
			storage.put("pending obsolete name", crystal);
			Assertions.assertTrue(storage.removeAll(List.of(crystal, crystal, new Object())));
			Assertions.assertEquals(Set.of(other), allElements(storage));
			storage.put("discarded replacement", crystal);
			Assertions.assertTrue(storage.removeAll(List.of(crystal)));
			storage.put("discovered aspect " + batch, crystal);
			Assertions.assertEquals(Set.of(crystal), search(storage, "discovered aspect " + batch));
			Assertions.assertEquals(Set.of(other), search(storage, "crystal"));
			Assertions.assertTrue(search(storage, "undiscovered").isEmpty());
			Assertions.assertTrue(search(storage, "pending").isEmpty());
			Assertions.assertTrue(search(storage, "discarded").isEmpty());
			Assertions.assertEquals(Set.of(crystal, other), allElements(storage));
		}
		Assertions.assertTrue(storage.removeAll(List.of(crystal, other)));
		Assertions.assertTrue(search(storage, "aspect").isEmpty());
		Assertions.assertTrue(allElements(storage).isEmpty());
		storage.put("new crystal", other);
		Assertions.assertEquals(Set.of(other), search(storage, "crystal"));
	}

	@Test
	public void removesValuesByIdentityIncludingOversizedAndUncommittedKeys() {
		String first = new String("equal value");
		String second = new String("equal value");
		ISearchStorage<String> storage = new CompactSearchStorageBuilder<String>().build();
		storage.put("x".repeat(32_767), first);
		storage.put("pending", first);
		storage.put("retained", second);
		Assertions.assertTrue(storage.removeAll(List.of(first)));
		Assertions.assertEquals(Set.of(second), allElements(storage));
		Assertions.assertTrue(search(storage, "pending").isEmpty());
		Assertions.assertSame(second, search(storage, "retained").iterator().next());
	}

	@Test
	public void removesAllUncommittedValuesBeforeTheFirstSearch() {
		ISearchStorage<Object> storage = new CompactSearchStorageBuilder<Object>().build();
		Object removed = new Object();
		storage.put("pending", removed);
		Assertions.assertTrue(storage.removeAll(List.of(removed)));
		Assertions.assertTrue(search(storage, "pending").isEmpty());
		Assertions.assertTrue(allElements(storage).isEmpty());
		Object replacement = new Object();
		storage.put("replacement", replacement);
		Assertions.assertEquals(Set.of(replacement), search(storage, "replacement"));
		Assertions.assertTrue(search(storage, "pending").isEmpty());
	}

	@Test
	public void removesSharedKeyMembersWithoutRemovingOtherValues() {
		LimitedStringStorageBuilder<String> builder = new LimitedStringStorageBuilder<>(CompactSearchStorageBuilder::new);
		String first = new String("equal value");
		String second = new String("equal value");
		builder.put("shared", first);
		builder.put("shared", second);
		builder.put("first only", first);
		ISearchStorage<String> storage = builder.build();
		Assertions.assertTrue(storage.removeAll(List.of(first)));
		Assertions.assertSame(second, search(storage, "shared").iterator().next());
		Assertions.assertEquals(1, search(storage, "shared").size());
		Assertions.assertTrue(search(storage, "first only").isEmpty());
		Assertions.assertSame(second, allElements(storage).iterator().next());
		storage.put("first only", first);
		Assertions.assertSame(first, search(storage, "first only").iterator().next());
		Assertions.assertTrue(storage.removeAll(List.of(first, second)));
		Assertions.assertTrue(allElements(storage).isEmpty());
	}

	private static <T> Set<T> search(ISearchStorage<T> storage, String token) {
		Set<T> results = Collections.newSetFromMap(new IdentityHashMap<>());
		storage.getSearchResults(token, results::addAll);
		if (storage instanceof LimitedStringStorage<T> limited) {
			Set<T> collected = Collections.newSetFromMap(new IdentityHashMap<>());
			limited.collectSearchResults(token, collected::add);
			Assertions.assertEquals(results, collected);
		}
		return results;
	}

	private static <T> Set<T> allElements(ISearchStorage<T> storage) {
		Set<T> results = Collections.newSetFromMap(new IdentityHashMap<>());
		storage.getAllElements(results::addAll);
		return results;
	}
}

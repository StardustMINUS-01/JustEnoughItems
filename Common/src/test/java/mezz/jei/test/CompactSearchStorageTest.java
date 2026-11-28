package mezz.jei.test;

import mezz.jei.api.search.ISearchStorage;
import mezz.jei.common.search.compact.CompactFieldIndex;
import mezz.jei.common.search.compact.CompactSearchStorageBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactSearchStorageTest {
	@Test
	void matchesExactUtf16SubstringsIncludingLongAndEmptyText() {
		var builder = new CompactSearchStorageBuilder<Integer>();
		builder.put("x".repeat(40000) + "𨭆", 1);
		builder.put("", 2);
		builder.put("*?", 3);
		var storage = builder.build();
		for (String token : List.of("x𨭆", "𨭆", "𨭆".substring(0, 1), "𨭆".substring(1))) {
			assertEquals(Set.of(1), search(storage, token));
		}
		assertEquals(Set.of(1, 2, 3), search(storage, ""));
		assertEquals(Set.of(3), search(storage, "?"));
		assertTrue(search(storage, "X").isEmpty());
		assertThrows(IllegalStateException.class, builder::build);
	}

	@Test
	void retainsIdsInPartialAndDistantBitmapBlocksAfterCompaction() {
		var builder = new CompactSearchStorageBuilder<Integer>();
		for (int id = 0; id < 8500; id++) {
			builder.put(id < 1024 || id >= 8192 ? "dense" : "other", id);
		}
		var storage = builder.build();
		var before = search(storage, "dense");
		assertEquals(1332, before.size());
		storage.removeAll(before);
		assertTrue(search(storage, "dense").isEmpty());
		assertEquals(7168, search(storage, "other").size());
	}

	@Test
	void removesByIdentityAcrossCompactionsAndReinsertion() {
		record Value(int number) {}
		var first = new Value(1);
		var second = new Value(1);
		var builder = new CompactSearchStorageBuilder<Value>();
		builder.put("old", first);
		builder.put("old", second);
		var storage = builder.build();
		assertEquals(2, search(storage, "old").size());
		storage.removeAll(List.of(first));
		storage.put("new", first);
		for (int i = 0; i < 300; i++) {
			storage.put("extra" + i, new Value(i));
		}
		assertTrue(search(storage, "old").contains(second));
		assertTrue(search(storage, "new").contains(first));
		assertEquals(1, search(storage, "old").size());
		storage.removeAll(List.of(first, second));
		assertTrue(search(storage, "old").isEmpty());
		assertTrue(search(storage, "new").isEmpty());
	}

	@Test
	void matchesScanAcrossNarrowingBackspaceAndHighFanout() {
		var builder = new CompactFieldIndex.Builder();
		var keys = new ArrayList<String>();
		for (int i = 0; i < 1300; i++) {
			String key = (i % 17 == 0 ? "𨭆\u0000\uffff𨭆" : "prefix_repeat_") + i;
			keys.add(key);
			for (int j = 31; j >= 0; j--) {
				builder.put(key, 4090 + i + j);
			}
		}
		var index = builder.build();
		var session = new CompactFieldIndex.Session();
		for (String token : List.of("p", "pr", "pre", "p", "prefix_repeat_2", "repeat_2", "prefix_repeat_22", "e", "e", "\u0000\uffff", "𨭆", "𨭆".substring(1), "missing", "")) {
			long[] result = new long[85];
			index.orMatches(token, result, session);
			BitSet expected = new BitSet();
			for (int i = 0; i < keys.size(); i++) {
				if (keys.get(i).contains(token)) {
					expected.set(4090 + i, 4122 + i);
				}
			}
			assertEquals(expected, BitSet.valueOf(result));
		}
	}

	private static <T> Set<T> search(ISearchStorage<T> storage, String token) {
		Set<T> result = Collections.newSetFromMap(new IdentityHashMap<>());
		storage.getSearchResults(token, result::addAll);
		return result;
	}
}

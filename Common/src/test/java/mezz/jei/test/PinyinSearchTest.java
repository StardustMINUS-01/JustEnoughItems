package mezz.jei.test;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import mezz.jei.common.search.compact.CompactSearchStorageBuilder;
import mezz.jei.common.search.compact.CompactFieldIndex;
import mezz.jei.common.search.compact.PinyinOption;
import mezz.jei.common.search.compact.PinyinQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinyinSearchTest {
	private static final int ENABLED = PinyinOption.ENABLED.bit;

	private static boolean matches(String text, String token, int flags) {
		var query = PinyinQuery.compile(token, flags);
		return text.contains(token) || query != null && query.matches(text);
	}

	@Test
	void matchesEveryTypingPrefixAndMixedScripts() {
		for (String token : List.of("xiangmuyuanmu", "xm原木", "红s", "zh块", "a1xm", "lü", "lv")) {
			String text = "A/a1橡木原木 红石 铸块 铝";
			for (int n = 1; n <= token.length(); n++) {
				assertTrue(matches(text, token.substring(0, n), ENABLED), token.substring(0, n));
			}
		}
		assertTrue(matches("铁".repeat(35), "tie".repeat(35), ENABLED));
		assertFalse(matches("铁".repeat(34), "tie".repeat(35), ENABLED));
		assertFalse(matches("工业铁块", "factory", ENABLED));
	}

	@Test
	void narrowingSurvivesBackspaceAndSupplementaryInput() {
		var builder = new CompactSearchStorageBuilder.Factory(() -> ENABLED).<String>create();
		builder.put("橡𬭊", "wood");
		builder.put("红石", "redstone");
		var storage = builder.build();
		for (String token : List.of("x", "x𬭊".substring(0, 2), "x𬭊", "x", "xd", "h", "hs", "x", "xdu")) {
			Set<String> hits = new HashSet<>();
			storage.getSearchResults(token, hits::addAll);
			Set<String> expected = new HashSet<>();
			if (matches("橡𬭊", token, ENABLED)) {
				expected.add("wood");
			}
			if (matches("红石", token, ENABLED)) {
				expected.add("redstone");
			}
			assertEquals(expected, hits, token);
		}
	}

	@Test
	void recognizesPolyphonicAndSupplementaryCharacters() {
		for (String pair : List.of("重 chong", "重 zhong", "长 zhang", "长 chang", "调 diao", "调 tiao", "行 hang", "行 xing",
			"乐 le", "乐 yue", "铁 tie", "鐵 tie", "𨭆 hei", "𬬭 lun", "𫓧 fu", "𫟼 da", "𬬹 shen", "𫇭 wei", "𬭊 du", "𬭛 bo")) {
			String[] parts = pair.split(" ");
			assertTrue(matches(parts[0], parts[1], ENABLED), pair);
			assertTrue(matches(parts[0], parts[1].substring(0, 1), ENABLED), pair);
		}
		assertFalse(matches("鐵", "铁", ENABLED));
		assertTrue(matches("鐵", "铁", PinyinOption.FOLD.bit));
		assertTrue(matches("铁", "鐵", PinyinOption.FOLD.bit));
	}

	@Test
	void fuzzyRecallAgreesWithScanningAcrossTypingAndPastedQueries() {
		var texts = List.of("日路你林", "你林日", "铸蓝山", "自南山", "金晶", "湖福", "NL红", "旁边");
		for (int fuzzy : List.of(PinyinOption.IN_ING.bit,
			PinyinOption.Z_ZH.bit | PinyinOption.L_N.bit | PinyinOption.AN_ANG.bit,
			PinyinOption.L_N.bit | PinyinOption.R_L.bit, PinyinOption.F_H.bit)) {
			int flags = ENABLED | fuzzy;
			var builder = new CompactSearchStorageBuilder.Factory(() -> flags).<String>create();
			texts.forEach(t -> builder.put(t, t));
			var storage = builder.build();
			for (String word : List.of("nnn", "rrr", "nlr", "ri", "zln", "zulanshang", "jinjing", "hfh", "NLh")) {
				for (int length : List.of(word.length(), 1, 2, word.length())) {
					String token = word.substring(0, length);
					var expected = new HashSet<String>();
					for (String text : texts) {
						if (matches(text, token, flags)) {
							expected.add(text);
						}
					}
					var actual = new HashSet<String>();
					storage.getSearchResults(token, actual::addAll);
					assertEquals(expected, actual, token);
				}
			}
		}
	}

	@Test
	void matchesLatinTiersBesideChineseNamesInIndexedAndUpdatedText() {
		var builder = new CompactSearchStorageBuilder.Factory(() -> ENABLED).<String>create();
		builder.put("HV 变压器", "base");
		builder.put("HV 配电箱", "other");
		builder.put("H V 变压器", "split tier");
		builder.put("HV-变压器", "punctuated tier");
		builder.put("prefix HVname", "upper");
		builder.put("prefix hvname", "lower");
		var storage = builder.build();
		storage.put("HV 变压器", "delta");
		for (String token : List.of("hvbyq", "HVbYq", "hv变压器")) {
			var hits = new HashSet<String>();
			storage.getSearchResults(token, hits::addAll);
			assertEquals(Set.of("base", "delta"), hits);
		}
		var hits = new HashSet<String>();
		storage.getSearchResults("HVNAME", hits::addAll);
		assertEquals(Set.of("upper", "lower"), hits);
		String longTier = "A".repeat(65);
		assertTrue(matches(longTier + " 变", longTier + "bian", ENABLED));
		assertFalse(matches("HV 变压器", "hvbyq", 0));
	}

	@Test
	void independentQuerySessionsCanRaceBackgroundWarmup() throws Exception {
		var builder = new CompactFieldIndex.Builder();
		builder.put("铁", 0);
		builder.put("红石", 1);
		var index = builder.build();
		var start = new java.util.concurrent.CountDownLatch(1);
		try (var workers = java.util.concurrent.Executors.newFixedThreadPool(3)) {
			var warming = workers.submit(() -> {
				start.await();
				index.warmPinyin(ENABLED);
				return null;
			});
			var results = List.of("tie", "hongshi").stream().map(token -> workers.submit(() -> {
				start.await();
				long[] hits = new long[1];
				index.orMatches(token, hits, new CompactFieldIndex.Session(), PinyinQuery.compile(token, ENABLED));
				return hits[0];
			}))
				.toList();
			start.countDown();
			warming.get();
			assertEquals(1L, results.getFirst().get());
			assertEquals(2L, results.getLast().get());
		}
	}

	@Test
	void foldingAloneMatchesBaseAndDeltaWithoutPhoneticReadings() {
		var builder = new CompactSearchStorageBuilder.Factory(() -> PinyinOption.FOLD.bit).<String>create();
		builder.put("鐵块", "base");
		var storage = builder.build();
		storage.put("鐵器", "delta");
		var hits = new HashSet<String>();
		storage.getSearchResults("铁", hits::addAll);
		assertEquals(Set.of("base", "delta"), hits);
		hits.clear();
		storage.getSearchResults("tie", hits::addAll);
		assertTrue(hits.isEmpty());
	}

	@Test
	void changesFuzzyMatchingOnTheLiveStorage() {
		AtomicInteger options = new AtomicInteger();
		var builder = new CompactSearchStorageBuilder.Factory(options::get).<String>create();
		builder.put("铸块", "base");
		var storage = builder.build();
		storage.put("铸件", "delta");
		for (int flags : new int[]{0, ENABLED, ENABLED | PinyinOption.Z_ZH.bit, ENABLED, 0}) {
			options.set(flags);
			Set<String> hits = new HashSet<>();
			storage.getSearchResults("zu", hits::addAll);
			assertEquals((flags & PinyinOption.Z_ZH.bit) == 0 ? Set.of() : Set.of("base", "delta"), hits);
		}
	}

	@Test
	void supportsIndependentFuzzyFinalsAndInitials() {
		int linkedInitials = ENABLED | PinyinOption.L_N.bit | PinyinOption.R_L.bit;
		assertTrue(matches("日", "ni", linkedInitials));
		assertTrue(matches("你", "ri", linkedInitials));
		String[] source = {"铸", "吃", "石", "路", "日", "湖", "山", "真", "金", "天", "关"};
		String[] token = {"zu", "ci", "si", "nu", "li", "fu", "shang", "zheng", "jing", "tiang", "guang"};
		var options = PinyinOption.values();
		for (int i = 0; i < source.length; i++) {
			assertTrue(matches(source[i], token[i], ENABLED | options[i + 2].bit), token[i]);
		}
	}
}

package mezz.jei.test.bench;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The reference scenario catalog for the JEI search benchmark, mirroring the
 * Thunderbolt {@code ThunderboltReferenceScenarios} catalog. Each capability
 * family is exercised under all three data modes (MISSING / MINIMUM / UNBOUNDED).
 */
public final class JeiSearchReferenceScenarios {
	/** Smallest useful dataset: the two base plugin ingredients. */
	public static final int SCALE_MINIMUM = 2;
	/** Small unbounded-style dataset for warmup-grade scale probes. */
	public static final int SCALE_SMALL = 100;
	/** Large dataset (5000 ingredients) for realistic-scale probes. */
	public static final int SCALE_LARGE = 5000;
	/** Very large dataset (10000 ingredients) for stress probes. */
	public static final int SCALE_HUGE = 10000;

	private JeiSearchReferenceScenarios() {
	}

	public static List<JeiSearchReferenceScenario> all() {
		return List.of(
			// --- name/prefix: plain prefix search on display names ---
			scenario("name/prefix", JeiSearchCapability.NAME_PREFIX, JeiDataMode.MISSING, SCALE_MINIMUM, "nonexistentprefix", Set.of()),
			scenario("name/prefix", JeiSearchCapability.NAME_PREFIX, JeiDataMode.MINIMUM, SCALE_MINIMUM, "\"test ingredient display name testingredient#0\"", Set.of(0)),
			scenario("name/prefix", JeiSearchCapability.NAME_PREFIX, JeiDataMode.UNBOUNDED, SCALE_LARGE, "\"test ingredient display name testingredient#4999\"", Set.of(4999)),

			// --- name/substring: plain substring search ---
			scenario("name/substring", JeiSearchCapability.NAME_SUBSTRING, JeiDataMode.MISSING, SCALE_MINIMUM, "nonexistent", Set.of()),
			scenario("name/substring", JeiSearchCapability.NAME_SUBSTRING, JeiDataMode.MINIMUM, SCALE_MINIMUM, "ingredient display", Set.of(0, 1)),
			scenario("name/substring", JeiSearchCapability.NAME_SUBSTRING, JeiDataMode.UNBOUNDED, SCALE_LARGE, "\"display name testingredient#2500\"", Set.of(2500)),

			// --- name/suffix: plain suffix search (unprefixed number token) ---
			scenario("name/suffix", JeiSearchCapability.NAME_SUFFIX, JeiDataMode.MISSING, SCALE_MINIMUM, "9999", Set.of()),
			scenario("name/suffix", JeiSearchCapability.NAME_SUFFIX, JeiDataMode.MINIMUM, SCALE_MINIMUM, "1", Set.of(1)),
			scenario("name/suffix", JeiSearchCapability.NAME_SUFFIX, JeiDataMode.UNBOUNDED, SCALE_LARGE, "4999", Set.of(4999)),

			// --- name/multi-token: intersection of several unprefixed tokens ---
			scenario("name/multi-token", JeiSearchCapability.NAME_MULTI_TOKEN, JeiDataMode.MISSING, SCALE_MINIMUM, "test nonexistent", Set.of()),
			scenario("name/multi-token", JeiSearchCapability.NAME_MULTI_TOKEN, JeiDataMode.MINIMUM, SCALE_MINIMUM, "test 0", Set.of(0)),
			scenario("name/multi-token", JeiSearchCapability.NAME_MULTI_TOKEN, JeiDataMode.UNBOUNDED, SCALE_LARGE, "ingredient 4999", Set.of(4999)),

			// --- mod/prefix: '@' prefixed mod-name search ---
			scenario("mod/prefix", JeiSearchCapability.MOD_PREFIX, JeiDataMode.MISSING, SCALE_MINIMUM, "@nonexistentmod", Set.of()),
			scenario("mod/prefix", JeiSearchCapability.MOD_PREFIX, JeiDataMode.MINIMUM, SCALE_MINIMUM, "@ModName(jei_test_mod)", Set.of(0, 1)),
			scenario("mod/prefix", JeiSearchCapability.MOD_PREFIX, JeiDataMode.UNBOUNDED, SCALE_LARGE, "@ModName(jei_test_mod)", allNumbers(SCALE_LARGE)),

			// --- id/prefix: '&' prefixed identifier search ---
			scenario("id/prefix", JeiSearchCapability.ID_PREFIX, JeiDataMode.MISSING, SCALE_MINIMUM, "&jei_test_mod:test_ingredient_9999", Set.of()),
			scenario("id/prefix", JeiSearchCapability.ID_PREFIX, JeiDataMode.MINIMUM, SCALE_MINIMUM, "&jei_test_mod:test_ingredient_1", Set.of(1)),
			scenario("id/prefix", JeiSearchCapability.ID_PREFIX, JeiDataMode.UNBOUNDED, SCALE_LARGE, "&jei_test_mod:test_ingredient_4999", Set.of(4999)),

			// --- color/prefix: '^' prefixed color-name search ---
			scenario("color/prefix", JeiSearchCapability.COLOR_PREFIX, JeiDataMode.MISSING, SCALE_MINIMUM, "^nonexistentcolor", Set.of()),
			scenario("color/prefix", JeiSearchCapability.COLOR_PREFIX, JeiDataMode.MINIMUM, SCALE_MINIMUM, "^black", Set.of(0, 1)),
			scenario("color/prefix", JeiSearchCapability.COLOR_PREFIX, JeiDataMode.UNBOUNDED, SCALE_LARGE, "^black", allNumbers(SCALE_LARGE)),

			// --- exact-name: full-string display-name search ---
			scenario("exact-name", JeiSearchCapability.EXACT_NAME, JeiDataMode.MISSING, SCALE_MINIMUM, "\"test ingredient display name testingredient#9999\"", Set.of()),
			scenario("exact-name", JeiSearchCapability.EXACT_NAME, JeiDataMode.MINIMUM, SCALE_MINIMUM, "\"test ingredient display name testingredient#0\"", Set.of(0)),
			scenario("exact-name", JeiSearchCapability.EXACT_NAME, JeiDataMode.UNBOUNDED, SCALE_LARGE, "\"test ingredient display name testingredient#4999\"", Set.of(4999)),

			// --- exclusion: '-token' exclusion search ---
			scenario("exclusion", JeiSearchCapability.EXCLUSION, JeiDataMode.MISSING, SCALE_MINIMUM, "test -nonexistent", Set.of(0, 1)),
			scenario("exclusion", JeiSearchCapability.EXCLUSION, JeiDataMode.MINIMUM, SCALE_MINIMUM, "test -1", Set.of(0)),
			scenario("exclusion", JeiSearchCapability.EXCLUSION, JeiDataMode.UNBOUNDED, SCALE_LARGE, "test -4999", allExcept(SCALE_LARGE, 4999)),

			// --- large-scale: dataset size sensitivity ---
			scenario("large-scale", JeiSearchCapability.LARGE_SCALE, JeiDataMode.MISSING, SCALE_HUGE, "99999", Set.of()),
			scenario("large-scale", JeiSearchCapability.LARGE_SCALE, JeiDataMode.MINIMUM, SCALE_SMALL, "99", Set.of(99)),
			scenario("large-scale", JeiSearchCapability.LARGE_SCALE, JeiDataMode.UNBOUNDED, SCALE_HUGE, "9999", Set.of(9999))
		);
	}

	private static JeiSearchReferenceScenario scenario(
		String id,
		JeiSearchCapability capability,
		JeiDataMode dataMode,
		int scale,
		String query,
		Set<Integer> expected
	) {
		return new JeiSearchReferenceScenario(id, capability, dataMode, scale, query, expected);
	}

	private static Set<Integer> allNumbers(int scale) {
		Set<Integer> numbers = new LinkedHashSet<>(scale);
		for (int i = 0; i < scale; i++) {
			numbers.add(i);
		}
		return numbers;
	}

	private static Set<Integer> allExcept(int scale, int excluded) {
		Set<Integer> numbers = allNumbers(scale);
		numbers.remove(excluded);
		return numbers;
	}
}

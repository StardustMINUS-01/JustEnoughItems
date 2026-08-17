package mezz.jei.test.bench;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Boundary-condition suite for the JEI search engine, mirroring the AE2VM
 * {@code Ae2VmBoundaryCapabilitySuiteTest}: degenerate queries, malformed
 * input, and extreme tokens must neither crash the engine nor hang it.
 * One {@code [jei-boundary]} log line per case plus a trailing SUMMARY.
 */
public class JeiBoundaryCapabilitySuiteTest {
	private static final int SCALE = 100;

	private record BoundaryCase(String id, String query, String expected) {
	}

	private static final List<BoundaryCase> CASES = List.of(
		// --- degenerate queries ---
		new BoundaryCase("empty-query", "", "empty"),
		new BoundaryCase("whitespace-only", "   ", "empty"),
		new BoundaryCase("tab-only", "\t\n", "empty"),
		// --- single-character tokens ---
		new BoundaryCase("single-char-match", "t", "contains-0"),
		new BoundaryCase("single-char-nomatch", "z", "empty"),
		new BoundaryCase("single-char-exclusion", "-", "empty"),
		new BoundaryCase("single-char-prefix-at", "@", "empty"),
		new BoundaryCase("single-char-prefix-hash", "#", "empty"),
		new BoundaryCase("single-char-prefix-amp", "&", "empty"),
		new BoundaryCase("single-char-prefix-caret", "^", "empty"),
		// --- exclusion-only query ---
		new BoundaryCase("exclusion-only", "-test", "empty"),
		new BoundaryCase("exclusion-prefix-only", "-@mod", "empty"),
		// --- no-match tokens ---
		new BoundaryCase("no-match-long", "zzzzzzzzzz", "empty"),
		new BoundaryCase("no-match-huge-token", "a".repeat(1000), "empty"),
		new BoundaryCase("no-match-special-chars", "!@#$%^&*()", "empty"),
		// --- prefix with no search text ---
		new BoundaryCase("prefix-at-only", "@", "empty"),
		new BoundaryCase("prefix-at-whitespace", "@ ", "empty"),
		// --- quotes ---
		new BoundaryCase("quote-escaped", "\"test ingredient display name testingredient#0\"", "contains-0"),
		new BoundaryCase("quote-unterminated", "\"test", "contains-0"),
		// --- backslash escaping ---
		new BoundaryCase("backslash-escape", "\\t", "contains-0"),
		new BoundaryCase("trailing-backslash", "test\\", "contains-0"),
		// --- duplicate / repeated tokens ---
		new BoundaryCase("repeated-token", "test test test", "contains-0"),
		// --- case sensitivity ---
		new BoundaryCase("uppercase-query", "\"TEST INGREDIENT DISPLAY NAME TESTINGREDIENT#0\"", "contains-0"),
		new BoundaryCase("mixed-case-query", "\"TeSt InGrEdIeNt DiSpLaY NaMe TeStInGrEdIeNt#0\"", "contains-0"),
		// --- null safety ---
		new BoundaryCase("null-query", null, "empty")
	);

	@TestFactory
	public Stream<DynamicTest> boundarySuite() {
		List<DynamicTest> tests = new ArrayList<>(CASES.size() + 1);
		for (BoundaryCase c : CASES) {
			tests.add(DynamicTest.dynamicTest(
				"boundary " + c.id(),
				() -> runCase(c)
			));
		}
		tests.add(DynamicTest.dynamicTest(
			"boundary summary",
			this::printSummary
		));
		return tests.stream();
	}

	private void runCase(BoundaryCase c) {
		JeiSearchEngine engine = null;
		JeiBenchRunner.Outcome<String> outcome = JeiBenchRunner.invoke(() -> {
			JeiSearchEngine.EngineResult engineResult = JeiSearchEngine.create(SCALE);
			JeiSearchEngine e = engineResult.engine();
			Set<Integer> results = (c.query() == null) ? Set.of() : e.search(c.query());
			return describe(results);
		});

		JeiSupportStatus status = classify(outcome, c);
		System.out.printf(
			"[jei-boundary] engine=jei-search id=%s query=%s status=%s actual=%s expected=%s elapsedMs=%.3f%n",
			c.id(),
			formatQuery(c.query()),
			status,
			outcome.completed() ? outcome.value() : "n/a",
			c.expected(),
			outcome.elapsedMs()
		);
	}

	private static String describe(Set<Integer> results) {
		if (results.isEmpty()) {
			return "empty";
		}
		if (results.contains(0)) {
			return "contains-0";
		}
		return "non-empty";
	}

	private static JeiSupportStatus classify(
		JeiBenchRunner.Outcome<String> outcome,
		BoundaryCase c
	) {
		if (outcome.timedOut()) {
			return JeiSupportStatus.ENGINE_TIMEOUT;
		}
		if (!outcome.completed()) {
			return JeiSupportStatus.ENGINE_ERROR;
		}
		return outcome.value().equals(c.expected()) ?
			JeiSupportStatus.SUPPORTED :
			JeiSupportStatus.FALSE_NEGATIVE;
	}

	private static String formatQuery(String query) {
		if (query == null) {
			return "null";
		}
		if (query.isBlank()) {
			return "'" + query.replace("\n", "\\n").replace("\t", "\\t") + "'";
		}
		if (query.length() > 40) {
			return query.substring(0, 37) + "...";
		}
		return query;
	}

	private void printSummary() {
		long totalElapsedNanos = 0;
		int supported = 0;
		int falseNegative = 0;
		int engineError = 0;
		int timeout = 0;
		for (BoundaryCase c : CASES) {
			JeiBenchRunner.Outcome<String> outcome = JeiBenchRunner.invoke(() -> {
				JeiSearchEngine.EngineResult engineResult = JeiSearchEngine.create(SCALE);
				JeiSearchEngine e = engineResult.engine();
				Set<Integer> results = (c.query() == null) ? Set.of() : e.search(c.query());
				return describe(results);
			});
			totalElapsedNanos += outcome.elapsedNanos();
			switch (classify(outcome, c)) {
				case SUPPORTED -> supported++;
				case FALSE_NEGATIVE -> falseNegative++;
				case ENGINE_ERROR -> engineError++;
				case ENGINE_TIMEOUT -> timeout++;
				case FALSE_POSITIVE -> {
				}
			}
		}
		System.out.printf(
			"[jei-boundary] engine=jei-search SUMMARY cases=%d supported=%d falseNegative=%d engineError=%d timeout=%d totalElapsedMs=%.3f%n",
			CASES.size(),
			supported,
			falseNegative,
			engineError,
			timeout,
			totalElapsedNanos / 1_000_000.0
		);
	}
}

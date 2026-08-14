package mezz.jei.test.bench;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reference capability suite for the JEI search engine, mirroring the AE2VM
 * {@code Ae2VmReferenceCapabilitySuiteTest} structure: one dynamic test per
 * scenario plus a trailing summary test, with one {@code [jei-reference]} log
 * line per scenario and a final SUMMARY line.
 */
public class JeiSearchReferenceCapabilitySuiteTest {
	private record SearchRun(JeiSearchEngine engine, Set<Integer> results, long buildNanos) {
	}

	@TestFactory
	public Stream<DynamicTest> referenceCapabilitySuite() {
		List<JeiSearchReferenceScenario> scenarios = JeiSearchReferenceScenarios.all();
		List<DynamicTest> tests = new ArrayList<>(scenarios.size() + 1);
		for (JeiSearchReferenceScenario scenario : scenarios) {
			tests.add(DynamicTest.dynamicTest(
				"search " + scenario.id(),
				() -> runScenario(scenario)
			));
		}
		tests.add(DynamicTest.dynamicTest(
			"search summary",
			() -> printSummary(scenarios)
		));
		return tests.stream();
	}

	private void runScenario(JeiSearchReferenceScenario scenario) {
		JeiBenchRunner.Outcome<SearchRun> outcome = JeiBenchRunner.invoke(() -> {
			JeiSearchEngine.EngineResult engineResult = JeiSearchEngine.create(scenario.scale());
			Set<Integer> results = engineResult.engine().search(scenario.query());
			return new SearchRun(engineResult.engine(), results, engineResult.buildNanos());
		});

		JeiSupportStatus status = classify(outcome, scenario);
		System.out.printf(
			"[jei-reference] engine=jei-search id=%s capability=%s mode=%s scale=%d status=%s elapsedMs=%.3f buildMs=%.3f results=%d expected=%d%n",
			scenario.id(),
			scenario.capability(),
			scenario.dataMode(),
			scenario.scale(),
			status,
			outcome.elapsedMs(),
			outcome.completed() ? outcome.value().buildNanos() / 1_000_000.0 : -1,
			outcome.completed() ? outcome.value().results().size() : -1,
			scenario.expected().size()
		);
	}

	private static JeiSupportStatus classify(
		JeiBenchRunner.Outcome<SearchRun> outcome,
		JeiSearchReferenceScenario scenario
	) {
		if (outcome.timedOut()) {
			return JeiSupportStatus.ENGINE_TIMEOUT;
		}
		if (!outcome.completed()) {
			return JeiSupportStatus.ENGINE_ERROR;
		}
		Set<Integer> actual = outcome.value().results();
		Set<Integer> expected = scenario.expected();
		if (actual.equals(expected)) {
			return JeiSupportStatus.SUPPORTED;
		}
		if (actual.containsAll(expected) && actual.size() > expected.size()) {
			return JeiSupportStatus.FALSE_POSITIVE;
		}
		return JeiSupportStatus.FALSE_NEGATIVE;
	}

	private void printSummary(List<JeiSearchReferenceScenario> scenarios) {
		long totalElapsedNanos = 0;
		int supported = 0;
		int falsePositive = 0;
		int falseNegative = 0;
		int engineError = 0;
		int timeout = 0;
		for (JeiSearchReferenceScenario scenario : scenarios) {
			JeiBenchRunner.Outcome<SearchRun> outcome = JeiBenchRunner.invoke(() -> {
				JeiSearchEngine.EngineResult engineResult = JeiSearchEngine.create(scenario.scale());
				Set<Integer> results = engineResult.engine().search(scenario.query());
				return new SearchRun(engineResult.engine(), results, engineResult.buildNanos());
			});
			totalElapsedNanos += outcome.elapsedNanos();
			switch (classify(outcome, scenario)) {
				case SUPPORTED -> supported++;
				case FALSE_POSITIVE -> falsePositive++;
				case FALSE_NEGATIVE -> falseNegative++;
				case ENGINE_ERROR -> engineError++;
				case ENGINE_TIMEOUT -> timeout++;
			}
		}
		System.out.printf(
			"[jei-reference] engine=jei-search SUMMARY cases=%d supported=%d falsePositive=%d falseNegative=%d engineError=%d timeout=%d totalElapsedMs=%.3f%n",
			scenarios.size(),
			supported,
			falsePositive,
			falseNegative,
			engineError,
			timeout,
			totalElapsedNanos / 1_000_000.0
		);
	}
}

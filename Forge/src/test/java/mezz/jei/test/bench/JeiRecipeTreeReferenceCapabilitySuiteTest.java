package mezz.jei.test.bench;

import mezz.jei.gui.favorites.FavoriteTreeBuilder;
import mezz.jei.gui.input.FocusedRecipe;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reference capability suite for the JEI recipe-tree expansion engine
 * ({@link FavoriteTreeBuilder}), mirroring the Thunderbolt craft-plan graph
 * families. One dynamic test per scenario plus a trailing summary test,
 * with one {@code [jei-reference]} log line per scenario.
 */
public class JeiRecipeTreeReferenceCapabilitySuiteTest {
	private record TreeRun(int recipes, long buildNanos) {
	}

	@TestFactory
	public Stream<DynamicTest> referenceCapabilitySuite() {
		List<JeiRecipeTreeScenario> scenarios = JeiRecipeTreeReferenceScenarios.all();
		List<DynamicTest> tests = new ArrayList<>(scenarios.size() + 1);
		for (JeiRecipeTreeScenario scenario : scenarios) {
			tests.add(DynamicTest.dynamicTest(
				"tree " + scenario.id(),
				() -> runScenario(scenario)
			));
		}
		tests.add(DynamicTest.dynamicTest(
			"tree summary",
			() -> printSummary(scenarios)
		));
		return tests.stream();
	}

	private void runScenario(JeiRecipeTreeScenario scenario) {
		JeiBenchRunner.Outcome<TreeRun> outcome = JeiBenchRunner.invoke(() -> {
			long start = System.nanoTime();
			FavoriteTreeBuilder builder = new FavoriteTreeBuilder(
				scenario.store(),
				recipe -> Optional.ofNullable(scenario.graph().get(recipe))
			);
			FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(scenario.root(), scenario.depth());
			long buildNanos = System.nanoTime() - start;
			return new TreeRun(result.recipes().size(), buildNanos);
		});

		JeiSupportStatus status = classify(outcome, scenario);
		System.out.printf(
			"[jei-reference] engine=jei-tree id=%s family=%s mode=%s graphSize=%d depth=%d status=%s recipes=%d expected=%d elapsedMs=%.3f%n",
			scenario.id(),
			scenario.family(),
			scenario.dataMode(),
			scenario.graphSize(),
			scenario.depth(),
			status,
			outcome.completed() ? outcome.value().recipes() : -1,
			scenario.expectedRecipes(),
			outcome.elapsedMs()
		);
	}

	private static JeiSupportStatus classify(
		JeiBenchRunner.Outcome<TreeRun> outcome,
		JeiRecipeTreeScenario scenario
	) {
		if (outcome.timedOut()) {
			return JeiSupportStatus.ENGINE_TIMEOUT;
		}
		if (!outcome.completed()) {
			return JeiSupportStatus.ENGINE_ERROR;
		}
		if (outcome.value().recipes() == scenario.expectedRecipes()) {
			return JeiSupportStatus.SUPPORTED;
		}
		return outcome.value().recipes() > scenario.expectedRecipes() ?
			JeiSupportStatus.FALSE_POSITIVE :
			JeiSupportStatus.FALSE_NEGATIVE;
	}

	private void printSummary(List<JeiRecipeTreeScenario> scenarios) {
		long totalElapsedNanos = 0;
		int supported = 0;
		int falsePositive = 0;
		int falseNegative = 0;
		int engineError = 0;
		int timeout = 0;
		for (JeiRecipeTreeScenario scenario : scenarios) {
			JeiBenchRunner.Outcome<TreeRun> outcome = JeiBenchRunner.invoke(() -> {
				long start = System.nanoTime();
				FavoriteTreeBuilder builder = new FavoriteTreeBuilder(
					scenario.store(),
					recipe -> Optional.ofNullable(scenario.graph().get(recipe))
				);
				FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(scenario.root(), scenario.depth());
				long buildNanos = System.nanoTime() - start;
				return new TreeRun(result.recipes().size(), buildNanos);
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
			"[jei-reference] engine=jei-tree SUMMARY cases=%d supported=%d falsePositive=%d falseNegative=%d engineError=%d timeout=%d totalElapsedMs=%.3f%n",
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

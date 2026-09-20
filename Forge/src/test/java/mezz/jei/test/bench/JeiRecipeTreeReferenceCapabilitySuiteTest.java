package mezz.jei.test.bench;

import mezz.jei.gui.favorites.FavoriteTreeBuilder;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reference capability suite for the JEI recipe-tree expansion engine
 * ({@link FavoriteTreeBuilder}), mirroring the Thunderbolt craft-plan graph
 * families. One dynamic test per scenario plus a trailing summary test,
 * with one {@code [jei-reference]} log line per scenario.
 */
public class JeiRecipeTreeReferenceCapabilitySuiteTest {
	@Test
	public void expandsGeneratedFavoritesAndKeepsManualPriority() {
		var type = new ResourceLocation("test", "crafting");
		var root = new FocusedRecipe(type, new ResourceLocation("test", "root"));
		var middle = new FocusedRecipe(type, new ResourceLocation("test", "middle"));
		var leaf = new FocusedRecipe(type, new ResourceLocation("test", "leaf"));
		var end = new FocusedRecipe(type, new ResourceLocation("test", "end"));
		var manual = new FocusedRecipe(type, new ResourceLocation("test", "manual"));
		var a = BookmarkIngredientKey.of("test:item", "a");
		var b = BookmarkIngredientKey.of("test:item", "b");
		var c = BookmarkIngredientKey.of("test:item", "c");
		var d = BookmarkIngredientKey.of("test:item", "d");
		var e = BookmarkIngredientKey.of("test:item", "e");
		var graph = Map.of(
			root, new FavoriteTreeBuilder.ResolvedRecipe(root, List.of(new FavoriteTreeBuilder.ResolvedInput(0, a, List.of(a, b), List.of()))),
			middle, new FavoriteTreeBuilder.ResolvedRecipe(middle, List.of(new FavoriteTreeBuilder.ResolvedInput(0, d, List.of(c, d), List.of()))),
			leaf, new FavoriteTreeBuilder.ResolvedRecipe(leaf, List.of(new FavoriteTreeBuilder.ResolvedInput(0, e, List.of(e), List.of()))),
			end, new FavoriteTreeBuilder.ResolvedRecipe(end, List.of()),
			manual, new FavoriteTreeBuilder.ResolvedRecipe(manual, List.of())
		);
		var store = new FavoriteRecipeStore();
		store.setFavorite(BookmarkIngredientKey.of("test:item", "saved"), middle,
			Map.of(0, new FavoriteRecipeStore.FavoriteSlotInput(c, List.of(c, d))));
		store.setGeneratedFavoriteResolver(key -> Optional.ofNullable(Map.of(a, middle, c, leaf, e, end).get(key)));
		var builder = new FavoriteTreeBuilder(store, recipe -> Optional.ofNullable(graph.get(recipe)));
		Assertions.assertEquals(List.of(root, middle, leaf, end), builder.build(root, 3).recipes().stream().map(FavoriteTreeBuilder.FavoriteTreeRecipe::recipe).toList());
		Assertions.assertEquals(1, builder.build(root, 0).recipes().size());
		store.setFavorite(a, manual, Map.of());
		Assertions.assertEquals(List.of(root, manual), builder.build(root, 3).recipes().stream().map(FavoriteTreeBuilder.FavoriteTreeRecipe::recipe).toList());
	}

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
		return outcome.value().recipes() > scenario.expectedRecipes() ? JeiSupportStatus.FALSE_POSITIVE : JeiSupportStatus.FALSE_NEGATIVE;
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

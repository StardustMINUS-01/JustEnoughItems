package mezz.jei.test.bench;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.AutoCraftingManager;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reference capability suite for the JEI expansion-chain engine
 * ({@link RecipeChainMath} + {@link AutoCraftingManager}, the "expansion
 * chain" that shift+C drives). One dynamic test per scenario plus a trailing
 * summary test, with one {@code [jei-reference]} log line per scenario.
 *
 * <p>The ground truth is the "craft the set quantity regardless of inventory"
 * semantics: shift+C must produce exactly the requested batch of the final
 * result even when the inventory already holds a full batch. The
 * {@code chain/full-batch}, {@code chain/excess}, {@code multiplier/full-batch}
 * and {@code multiplier/excess} scenarios expose the Bug6 regression: with the
 * buggy {@code expandRootDemandForCraftAll} the chain crafts nothing when the
 * inventory already holds a full batch.</p>
 */
public class JeiRecipeChainReferenceCapabilitySuiteTest {
	private record ChainRun(Map<ResourceLocation, Long> crafted, long buildNanos) {
	}

	@TestFactory
	public Stream<DynamicTest> referenceCapabilitySuite() {
		List<JeiChainScenario> scenarios = JeiChainReferenceScenarios.all();
		List<DynamicTest> tests = new ArrayList<>(scenarios.size() + 1);
		for (JeiChainScenario scenario : scenarios) {
			tests.add(DynamicTest.dynamicTest(
				"chain " + scenario.id(),
				() -> runScenario(scenario)
			));
		}
		tests.add(DynamicTest.dynamicTest(
			"chain summary",
			() -> printSummary(scenarios)
		));
		return tests.stream();
	}

	private void runScenario(JeiChainScenario scenario) {
		JeiBenchRunner.Outcome<ChainRun> outcome = JeiBenchRunner.invoke(() -> {
			long start = System.nanoTime();
			TestInventory inventory = new TestInventory(scenario);
			RecipeChainMath math = RecipeChainMath.of(scenario.inputs(), scenario.collapsedRecipes());
			math.expandRootDemandForCraftAll(scenario.inventory());
			AutoCraftingManager.run(
				math,
				List.of(),
				inventory::snapshot,
				inventory::craft
			);
			long buildNanos = System.nanoTime() - start;
			return new ChainRun(inventory.craftedAmounts(), buildNanos);
		});

		JeiSupportStatus status = classify(outcome, scenario);
		System.out.printf(
			"[jei-reference] engine=jei-chain id=%s family=%s mode=%s graphSize=%d depth=%d status=%s crafted=%s expected=%s elapsedMs=%.3f%n",
			scenario.id(),
			scenario.family(),
			scenario.dataMode(),
			scenario.graphSize(),
			scenario.depth(),
			status,
			outcome.completed() ? outcome.value().crafted() : Map.of(),
			scenario.expectedCrafted(),
			outcome.elapsedMs()
		);
	}

	private static JeiSupportStatus classify(
		JeiBenchRunner.Outcome<ChainRun> outcome,
		JeiChainScenario scenario
	) {
		if (outcome.timedOut()) {
			return JeiSupportStatus.ENGINE_TIMEOUT;
		}
		if (!outcome.completed()) {
			return JeiSupportStatus.ENGINE_ERROR;
		}
		Map<ResourceLocation, Long> crafted = outcome.value().crafted();
		if (crafted.equals(scenario.expectedCrafted())) {
			return JeiSupportStatus.SUPPORTED;
		}
		// FALSE_POSITIVE: crafted more than expected (extra recipes or higher amounts).
		// FALSE_NEGATIVE: crafted less than expected (the Bug6 regression).
		boolean extra = crafted.keySet().stream()
			.anyMatch(recipe -> !scenario.expectedCrafted().containsKey(recipe));
		boolean over = scenario.expectedCrafted().entrySet().stream()
			.anyMatch(entry -> crafted.getOrDefault(entry.getKey(), 0L) > entry.getValue());
		return (extra || over) ? JeiSupportStatus.FALSE_POSITIVE : JeiSupportStatus.FALSE_NEGATIVE;
	}

	private void printSummary(List<JeiChainScenario> scenarios) {
		long totalElapsedNanos = 0;
		int supported = 0;
		int falsePositive = 0;
		int falseNegative = 0;
		int engineError = 0;
		int timeout = 0;
		for (JeiChainScenario scenario : scenarios) {
			JeiBenchRunner.Outcome<ChainRun> outcome = JeiBenchRunner.invoke(() -> {
				long start = System.nanoTime();
				TestInventory inventory = new TestInventory(scenario);
				RecipeChainMath math = RecipeChainMath.of(scenario.inputs(), scenario.collapsedRecipes());
				math.expandRootDemandForCraftAll(scenario.inventory());
				AutoCraftingManager.run(
					math,
					List.of(),
					inventory::snapshot,
					inventory::craft
				);
				long buildNanos = System.nanoTime() - start;
				return new ChainRun(inventory.craftedAmounts(), buildNanos);
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
			"[jei-reference] engine=jei-chain SUMMARY cases=%d supported=%d falsePositive=%d falseNegative=%d engineError=%d timeout=%d totalElapsedMs=%.3f%n",
			scenarios.size(),
			supported,
			falsePositive,
			falseNegative,
			engineError,
			timeout,
			totalElapsedNanos / 1_000_000.0
		);
	}

	/**
	 * Simulated inventory + executor for one scenario. Seeded from the
	 * scenario's inventory snapshot; {@code craft} looks up the recipe
	 * definition, consumes consumed ingredients, checks (without consuming)
	 * catalyst ingredients, and produces the outputs.
	 */
	private static final class TestInventory {
		private final Map<BookmarkIngredientKey, Long> amounts = new LinkedHashMap<>();
		private final Map<ResourceLocation, Long> craftedAmounts = new LinkedHashMap<>();
		private final Map<ResourceLocation, JeiChainScenario.ChainRecipeDef> recipes;

		TestInventory(JeiChainScenario scenario) {
			this.recipes = scenario.recipes();
			for (RecipeChainInput input : scenario.inventory()) {
				BookmarkItemMetadata metadata = input.metadata();
				for (BookmarkIngredientKey key : metadata.permutations()) {
					amounts.merge(key, metadata.amount(), Long::sum);
				}
			}
		}

		long amount(BookmarkIngredientKey key) {
			return amounts.getOrDefault(key, 0L);
		}

		List<RecipeChainInput> snapshot() {
			List<RecipeChainInput> inputs = new ArrayList<>();
			int index = 100;
			for (Map.Entry<BookmarkIngredientKey, Long> entry : amounts.entrySet()) {
				if (entry.getValue() > 0) {
					inputs.add(new RecipeChainInput(index++, item(entry.getKey(), entry.getValue())));
				}
			}
			return inputs;
		}

		boolean craft(ResourceLocation recipeUid, int multiplier) {
			JeiChainScenario.ChainRecipeDef recipe = recipes.get(recipeUid);
			if (recipe == null) {
				return false;
			}
			for (JeiChainScenario.ChainIngredientDef ingredient : recipe.ingredients()) {
				long required = ingredient.amount() * multiplier;
				if (ingredient.consumed()) {
					if (!take(ingredient.key(), required)) {
						return false;
					}
				} else if (amount(ingredient.key()) < required) {
					return false;
				}
			}
			for (JeiChainScenario.ChainOutputDef output : recipe.outputs()) {
				add(output.key(), output.amount() * multiplier);
			}
			craftedAmounts.merge(recipeUid, (long) multiplier, Long::sum);
			return true;
		}

		Map<ResourceLocation, Long> craftedAmounts() {
			return Map.copyOf(craftedAmounts);
		}

		private void add(BookmarkIngredientKey key, long amount) {
			amounts.merge(key, amount, Long::sum);
		}

		private boolean take(BookmarkIngredientKey key, long amount) {
			long current = amount(key);
			if (current < amount) {
				return false;
			}
			add(key, -amount);
			return true;
		}

		private static BookmarkItemMetadata item(BookmarkIngredientKey key, long amount) {
			return new BookmarkItemMetadata(
				BookmarkGroupManager.DEFAULT_GROUP_ID,
				BookmarkItemType.ITEM,
				1,
				amount,
				BookmarkItemMetadata.CHANCE_FULL,
				null,
				null,
				java.util.Set.of(key)
			);
		}
	}
}
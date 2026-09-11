package mezz.jei.test.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.chain.RecipeChainGraph;
import mezz.jei.gui.bookmarks.chain.RecipeChainPlan;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.ingredient;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.input;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.key;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.result;

public class ChainPlanTest {
	private static final ResourceLocation PLATE = ResourceLocation.fromNamespaceAndPath("test", "plate");
	private static final ResourceLocation MACHINE = ResourceLocation.fromNamespaceAndPath("test", "machine");

	@Test
	public void prefersExactResult() {
		var plate = input(0, result(PLATE, key("plate"), 1, 1));
		var ingot = input(1, ingredient(PLATE, key("ingot"), 1));
		var machine = input(2, result(MACHINE, key("machine"), 1, 1));
		var candidates = Set.of(key("plate"), key("plate_variant"));
		var required = input(3, ingredient(MACHINE, key("plate"), 1).withPermutations(candidates));
		var exact = input(4, result(ResourceLocation.fromNamespaceAndPath("test", "exact_plate"),
			key("plate"), 1, 1).withPermutations(candidates));
		var graph = RecipeChainGraph.create(List.of(plate, ingot, machine, required, exact));

		Assertions.assertEquals(List.of(required), graph.ingredientsFor(MACHINE));
		Assertions.assertEquals(exact, graph.findPreferredResult(required, Set.of()).orElseThrow());
	}

	@Test
	public void excludesVisitedRecipes() {
		var plate = input(0, result(PLATE, key("plate"), 1, 1));
		var required = input(1, ingredient(MACHINE, key("plate"), 1));
		var graph = RecipeChainGraph.create(List.of(plate, required));

		Assertions.assertTrue(graph.findPreferredResult(required, Set.of(PLATE)).isEmpty());
	}

	@Test
	public void compilesDependencies() {
		var plate = input(0, result(PLATE, key("plate"), 1, 1));
		var ingot = input(1, ingredient(PLATE, key("ingot"), 1));
		var machine = input(2, result(MACHINE, key("machine"), 1, 1));
		var required = input(3, ingredient(MACHINE, key("plate"), 1));
		var plan = RecipeChainPlan.compile(List.of(plate, ingot, machine, required), Set.of());

		Assertions.assertEquals(Set.of(MACHINE), plan.outputRecipes().keySet());
		Assertions.assertEquals(plate, plan.preferredResults().get(required));
	}

	@Test
	public void breaksCycles() {
		var plate = input(0, result(PLATE, key("plate"), 1, 1));
		var needsMachine = input(1, ingredient(PLATE, key("machine"), 1));
		var machine = input(2, result(MACHINE, key("machine"), 1, 1));
		var needsPlate = input(3, ingredient(MACHINE, key("plate"), 1));
		var plan = RecipeChainPlan.compile(List.of(plate, needsMachine, machine, needsPlate), Set.of());

		Assertions.assertEquals(Set.of(PLATE), plan.outputRecipes().keySet());
		Assertions.assertEquals(machine, plan.preferredResults().get(needsMachine));
		Assertions.assertFalse(plan.preferredResults().containsKey(needsPlate));
	}
}

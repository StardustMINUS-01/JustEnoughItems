package mezz.jei.test.gui.match;

import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRule;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.match.IngredientExpression;
import mezz.jei.gui.match.IngredientMatchInfo;
import mezz.jei.gui.match.IngredientSelector;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PreferenceMatchingTest {
	private static final ResourceLocation CIRCUIT = ResourceLocation.parse("gtceu:lv_circuit");
	private static final ResourceLocation ASSEMBLER = ResourceLocation.parse("gtceu:assembler");

	@Test
	public void prefersInputTier() {
		RecipePreferenceRule rule = rule(
			"gtceu:lv_circuit",
			"gtceu:soldering_alloy & fluid:gtceu:soldering_alloy; fluid:gtceu:tin",
			null
		);
		RecipePreferenceCandidate soldered = candidate(
			"gtceu:assembler/soldered_circuit",
			List.of(item("gtceu:soldering_alloy"), fluid("gtceu:soldering_alloy")),
			List.of(item(CIRCUIT))
		);
		RecipePreferenceCandidate tin = candidate(
			"gtceu:assembler/tin_circuit",
			List.of(fluid("gtceu:tin")),
			List.of(item(CIRCUIT))
		);

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipe(
			List.of(soldered, tin)
		);

		Assertions.assertEquals(Optional.of(soldered.recipe()), selected);
	}

	@Test
	public void rejectsConflictingRanks() {
		RecipePreferenceRule rule = rule(
			"gtceu:lv_circuit",
			"gtceu:soldering_alloy; fluid:gtceu:tin",
			"gtceu:assembler/tin_*; gtceu:assembler/soldered_*"
		);
		RecipePreferenceCandidate soldered = candidate(
			"gtceu:assembler/soldered_circuit",
			List.of(item("gtceu:soldering_alloy")),
			List.of(item(CIRCUIT))
		);
		RecipePreferenceCandidate tin = candidate(
			"gtceu:assembler/tin_circuit",
			List.of(fluid("gtceu:tin")),
			List.of(item(CIRCUIT))
		);

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipe(
			List.of(soldered, tin)
		);

		Assertions.assertTrue(selected.isEmpty());
	}

	@Test
	public void preservesTies() {
		RecipePreferenceRule rule = rule(
			"gtceu:lv_circuit",
			"gtceu:soldering_alloy | fluid:gtceu:tin",
			null
		);
		RecipePreferenceCandidate soldered = candidate(
			"gtceu:assembler/soldered_circuit",
			List.of(item("gtceu:soldering_alloy")),
			List.of(item(CIRCUIT))
		);
		RecipePreferenceCandidate tin = candidate(
			"gtceu:assembler/tin_circuit",
			List.of(fluid("gtceu:tin")),
			List.of(item(CIRCUIT))
		);
		RecipePreferenceRules rules = new RecipePreferenceRules(List.of(rule));

		List<FocusedRecipe> preferred = rules.resolvePreferredRecipes(List.of(soldered, tin));

		Assertions.assertEquals(List.of(soldered.recipe(), tin.recipe()), preferred);
		Assertions.assertTrue(rules.resolvePreferredRecipe(List.of(soldered, tin)).isEmpty());
	}

	@Test
	public void matchesNegatedGroup() {
		RecipePreferenceRule rule = rule(
			"gtceu:lv_circuit",
			"gtceu:iron & !(#c:*ingot | fluid:gtceu:acid)",
			null
		);
		RecipePreferenceCandidate ironOnly = candidate(
			"test:iron_only",
			List.of(item("gtceu:iron")),
			List.of(item(CIRCUIT))
		);
		RecipePreferenceCandidate ironWithAcid = candidate(
			"test:iron_acid",
			List.of(item("gtceu:iron"), fluid("gtceu:acid")),
			List.of(item(CIRCUIT))
		);

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipe(
			List.of(ironOnly, ironWithAcid,
				candidate("test:iron_tagged", List.of(itemWithTag("gtceu:iron", "c:iron_ingot")), List.of(item(CIRCUIT))))
		);

		Assertions.assertEquals(Optional.of(ironOnly.recipe()), selected);
	}

	@Test
	public void matchesEitherOperand() {
		IngredientExpression expression = IngredientExpression
			.parseIngredient("#c:fine_wires | gtceu:*_wire")
			.orElseThrow();
		Assertions.assertTrue(expression.matches(itemWithTag("test:cobalt", "c:fine_wires")));
		Assertions.assertTrue(expression.matches(item("gtceu:cobalt_wire")));
		Assertions.assertFalse(expression.matches(item("minecraft:dirt")));
	}

	@ParameterizedTest
	@CsvSource({
		"fluid:gtceu:molten_*, gtceu:molten_tin, true, , true",
		"fluid:gtceu:molten_*, gtceu:molten_tin, false, , false",
		"#c:*ingot, minecraft:iron_ingot, false, c:iron_ingot, true",
		"#c:*ingot, minecraft:iron_nugget, false, c:iron_nugget, false",
		"*:item_storage_cell_*, ae2:item_storage_cell_1k, false, , true",
		"*:item_storage_cell_*, test:item_storage_cell_4k, false, , true",
		"*:item_storage_cell_*, ae2:energy_cell, false, , false",
		"*:ingots, minecraft:iron_ingot, false, c:ingots, false",
		"#*:ingots, minecraft:iron_ingot, false, c:ingots, true",
		"#*:ingots, minecraft:gold_ingot, false, gtceu:ingots, true",
		"#*:ingots, minecraft:iron_ingot, false, , false",
		"ae2*:item_storage_cell_*, ae2:item_storage_cell_1k, false, , true",
		"ae2*:item_storage_cell_*, ae2x:item_storage_cell_4k, false, , true",
		"ae2*:item_storage_cell_*, test:item_storage_cell_1k, false, , false"
	})
	public void matchesSelector(String pattern, String id, boolean isFluid, String tag, boolean expected) {
		IngredientMatchInfo ingredient = isFluid ? fluid(id) : item(id);
		if (tag != null) {
			ingredient = itemWithTag(id, tag);
		}
		Assertions.assertEquals(expected, IngredientSelector.parse(pattern).orElseThrow().matches(ingredient));
	}

	@Test
	public void ranksOutputs() {
		RecipePreferenceRule rule = rule(
			"gtceu:lv_circuit; #c:circuits",
			"gtceu:iron",
			null
		);
		RecipePreferenceCandidate lvCircuit = candidate(
			"test:lv_circuit",
			List.of(item("gtceu:iron")),
			List.of(item(CIRCUIT))
		);
		RecipePreferenceCandidate advancedCircuit = candidate(
			"test:advanced_circuit",
			List.of(item("gtceu:iron")),
			List.of(itemWithTag("gtceu:advanced_circuit", "c:circuits"))
		);

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipe(
			List.of(lvCircuit, advancedCircuit)
		);

		Assertions.assertEquals(Optional.of(lvCircuit.recipe()), selected);
	}

	@Test
	public void deduplicatesRecipes() {
		RecipePreferenceCandidate shared = candidate("test:glass_recipe", List.of(), List.of(item("minecraft:glass")));
		Optional<FocusedRecipe> selected = RecipePreferenceRules.EMPTY.resolvePreferredRecipe(List.of(shared, shared));

		Assertions.assertEquals(Optional.of(shared.recipe()), selected);
	}

	@Test
	public void rejectsUnrankedTies() {
		RecipePreferenceRule rule = rule("#c:glass_blocks", "#c:logs", null);
		IngredientMatchInfo glass = itemWithTag("minecraft:glass", "c:glass_blocks");

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipe(
			List.of(
				candidate("test:glass_recipe", List.of(), List.of(glass)),
				candidate("test:stained_recipe", List.of(), List.of(glass))
			)
		);

		Assertions.assertTrue(selected.isEmpty());
	}

	@Test
	public void requiresMatchingOutput() {
		RecipePreferenceRule rule = rule("minecraft:iron_ingot", "minecraft:iron_ore", null);
		IngredientMatchInfo glass = itemWithTag("minecraft:glass", "c:glass_blocks");

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipe(
			List.of(
				candidate("test:glass_recipe", List.of(item("minecraft:iron_ore")), List.of(glass)),
				candidate("test:stained_recipe", List.of(item("minecraft:sand")), List.of(glass))
			)
		);

		Assertions.assertTrue(selected.isEmpty());
	}

	private static RecipePreferenceRule rule(String outputExpr, String inputExpr, String recipeExpr) {
		IngredientExpression output = IngredientExpression.parseIngredient(outputExpr).orElseThrow();
		Optional<IngredientExpression> input = inputExpr == null ?
			Optional.empty() :
			Optional.of(IngredientExpression.parseIngredient(inputExpr).orElseThrow());
		Optional<IngredientExpression> recipe = recipeExpr == null ?
			Optional.empty() :
			Optional.of(IngredientExpression.parseUid(recipeExpr).orElseThrow());
		return new RecipePreferenceRule(output, input, recipe);
	}

	private static RecipePreferenceCandidate candidate(
		String recipeUid,
		List<IngredientMatchInfo> inputs,
		List<IngredientMatchInfo> outputs
	) {
		return new RecipePreferenceCandidate(
			new FocusedRecipe(ASSEMBLER, ResourceLocation.parse(recipeUid)),
			inputs,
			outputs
		);
	}

	private static IngredientMatchInfo item(String id) {
		return item(ResourceLocation.parse(id));
	}

	private static IngredientMatchInfo item(ResourceLocation id) {
		return IngredientMatchInfo.item(id, Set.of());
	}

	private static IngredientMatchInfo itemWithTag(String id, String tagId) {
		return IngredientMatchInfo.item(
			ResourceLocation.parse(id),
			Set.of(ResourceLocation.parse(tagId))
		);
	}

	private static IngredientMatchInfo fluid(String id) {
		return IngredientMatchInfo.fluid(ResourceLocation.parse(id), Set.of());
	}
}

package mezz.jei.test.gui.favorites.preferences;

import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.favorites.preferences.RecipePreferenceExpression;
import mezz.jei.gui.favorites.preferences.RecipePreferenceIngredientInfo;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRule;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.favorites.preferences.RecipePreferenceTarget;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RecipePreferenceRulesV2Test {
	private static final ResourceLocation CIRCUIT = ResourceLocation.parse("gtceu:lv_circuit");
	private static final ResourceLocation ASSEMBLER = ResourceLocation.parse("gtceu:assembler");

	@Test
	public void selectsCandidateMatchingTheFirstInputTier() {
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
	public void returnsEmptyWhenInputAndRecipeRanksConflict() {
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
	public void fluidTargetMatchesFluidIngredient() {
		RecipePreferenceTarget target = selector("fluid:gtceu:molten_*");

		Assertions.assertTrue(target.matches(fluid("gtceu:molten_tin")));
		Assertions.assertFalse(target.matches(item("gtceu:molten_tin")));
	}

	@Test
	public void tagWildcardMatchesAnyTagId() {
		RecipePreferenceTarget target = selector("#c:*ingot");

		Assertions.assertTrue(target.matches(itemWithTag("minecraft:iron_ingot", "c:iron_ingot")));
		Assertions.assertFalse(target.matches(itemWithTag("minecraft:iron_nugget", "c:iron_nugget")));
	}

	@Test
	public void parensAndNegationWork() {
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
			List.of(ironOnly, ironWithAcid)
		);

		Assertions.assertEquals(Optional.of(ironOnly.recipe()), selected);
	}

	@Test
	public void outputRankParticipatesInRanking() {
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
	public void slotRuleCollapsesVariantsToSingleRecipe() {
		RecipePreferenceRule rule = rule("#c:glass_blocks", "#c:logs", null);
		RecipePreferenceIngredientInfo glass = itemWithTag("minecraft:glass", "c:glass_blocks");
		RecipePreferenceIngredientInfo stained = itemWithTag("minecraft:white_stained_glass", "c:glass_blocks");
		RecipePreferenceCandidate shared = candidate("test:glass_recipe", List.of(), List.of(glass, stained));

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipe(
			List.of(shared, shared)
		);

		Assertions.assertEquals(Optional.of(shared.recipe()), selected);
	}

	@Test
	public void slotRuleReturnsEmptyWhenCandidatesConflictAndNoRuleMatches() {
		RecipePreferenceRule rule = rule("#c:glass_blocks", "#c:logs", null);
		RecipePreferenceIngredientInfo glass = itemWithTag("minecraft:glass", "c:glass_blocks");

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipe(
			List.of(
				candidate("test:glass_recipe", List.of(), List.of(glass)),
				candidate("test:stained_recipe", List.of(), List.of(glass))
			)
		);

		Assertions.assertTrue(selected.isEmpty());
	}

	@Test
	public void ruleDoesNotApplyWhenOutputDoesNotMatch() {
		RecipePreferenceRule rule = rule("minecraft:iron_ingot", "minecraft:iron_ore", null);
		RecipePreferenceIngredientInfo glass = itemWithTag("minecraft:glass", "c:glass_blocks");

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipe(
			List.of(
				candidate("test:glass_recipe", List.of(item("minecraft:iron_ore")), List.of(glass)),
				candidate("test:stained_recipe", List.of(item("minecraft:iron_ore")), List.of(glass))
			)
		);

		Assertions.assertTrue(selected.isEmpty());
	}

	private static RecipePreferenceRule rule(String outputExpr, String inputExpr, String recipeExpr) {
		RecipePreferenceExpression output = RecipePreferenceExpression.parseIngredient(outputExpr).orElseThrow();
		Optional<RecipePreferenceExpression> input = inputExpr == null ?
			Optional.empty() :
			Optional.of(RecipePreferenceExpression.parseIngredient(inputExpr).orElseThrow());
		Optional<RecipePreferenceExpression> recipe = recipeExpr == null ?
			Optional.empty() :
			Optional.of(RecipePreferenceExpression.parseUid(recipeExpr).orElseThrow());
		return new RecipePreferenceRule("test", output, input, recipe);
	}

	private static RecipePreferenceTarget selector(String value) {
		return RecipePreferenceTarget.parse(value).orElseThrow();
	}

	private static RecipePreferenceCandidate candidate(
		String recipeUid,
		List<RecipePreferenceIngredientInfo> inputs,
		List<RecipePreferenceIngredientInfo> outputs
	) {
		return new RecipePreferenceCandidate(
			new FocusedRecipe(ASSEMBLER, ResourceLocation.parse(recipeUid)),
			inputs,
			outputs
		);
	}

	private static RecipePreferenceIngredientInfo item(String id) {
		return item(ResourceLocation.parse(id));
	}

	private static RecipePreferenceIngredientInfo item(ResourceLocation id) {
		return RecipePreferenceIngredientInfo.item(id, Set.of());
	}

	private static RecipePreferenceIngredientInfo itemWithTag(String id, String tagId) {
		return RecipePreferenceIngredientInfo.item(
			ResourceLocation.parse(id),
			Set.of(ResourceLocation.parse(tagId))
		);
	}

	private static RecipePreferenceIngredientInfo fluid(String id) {
		return RecipePreferenceIngredientInfo.fluid(ResourceLocation.parse(id), Set.of());
	}
}

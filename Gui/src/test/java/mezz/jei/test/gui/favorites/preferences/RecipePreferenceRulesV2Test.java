package mezz.jei.test.gui.favorites.preferences;

import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
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
	public void selectsCandidateMatchingTheFirstCompleteInputTier() {
		RecipePreferenceRule rule = rule(
			List.of(
				List.of(selector("gtceu:soldering_alloy"), selector("fluid:gtceu:soldering_alloy")),
				List.of(selector("fluid:gtceu:tin"))
			),
			List.of()
		);
		RecipePreferenceCandidate soldered = candidate("gtceu:assembler/soldered_circuit",
			item("gtceu:soldering_alloy"), fluid("gtceu:soldering_alloy"));
		RecipePreferenceCandidate tin = candidate("gtceu:assembler/tin_circuit", fluid("gtceu:tin"));

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipe(
			item(CIRCUIT),
			List.of(soldered, tin)
		);

		Assertions.assertEquals(Optional.of(soldered.recipe()), selected);
	}

	@Test
	public void returnsEmptyWhenInputAndRecipeRanksConflict() {
		RecipePreferenceRule rule = rule(
			List.of(
				List.of(selector("gtceu:soldering_alloy")),
				List.of(selector("fluid:gtceu:tin"))
			),
			List.of(
				List.of("gtceu:assembler/tin_*"),
				List.of("gtceu:assembler/soldered_*")
			)
		);
		RecipePreferenceCandidate soldered = candidate("gtceu:assembler/soldered_circuit", item("gtceu:soldering_alloy"));
		RecipePreferenceCandidate tin = candidate("gtceu:assembler/tin_circuit", fluid("gtceu:tin"));

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipe(
			item(CIRCUIT),
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
	public void slotRuleCollapsesVariantsToSingleRecipe() {
		RecipePreferenceRule rule = new RecipePreferenceRule(
			"glass",
			RecipePreferenceTarget.tag(ResourceLocation.parse("c:glass_blocks")),
			Optional.empty(),
			List.of(),
			List.of()
		);
		RecipePreferenceIngredientInfo glass = itemWithTag("minecraft:glass", "c:glass_blocks");
		RecipePreferenceIngredientInfo stained = itemWithTag("minecraft:white_stained_glass", "c:glass_blocks");
		RecipePreferenceCandidate shared = candidate("test:glass_recipe");

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipeForSlot(
			List.of(glass, stained),
			List.of(shared, shared)
		);

		Assertions.assertEquals(Optional.of(shared.recipe()), selected);
	}

	@Test
	public void slotRuleReturnsEmptyWhenVariantsHaveConflictingCandidates() {
		RecipePreferenceRule rule = new RecipePreferenceRule(
			"glass",
			RecipePreferenceTarget.tag(ResourceLocation.parse("c:glass_blocks")),
			Optional.empty(),
			List.of(),
			List.of()
		);
		RecipePreferenceIngredientInfo glass = itemWithTag("minecraft:glass", "c:glass_blocks");
		RecipePreferenceIngredientInfo stained = itemWithTag("minecraft:white_stained_glass", "c:glass_blocks");

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipeForSlot(
			List.of(glass, stained),
			List.of(candidate("test:glass_recipe"), candidate("test:stained_recipe"))
		);

		Assertions.assertTrue(selected.isEmpty());
	}

	@Test
	public void slotRuleDoesNotApplyWhenTargetMatchesNoVariant() {
		RecipePreferenceRule rule = new RecipePreferenceRule(
			"iron",
			RecipePreferenceTarget.item(ResourceLocation.parse("minecraft:iron_ingot")),
			Optional.empty(),
			List.of(),
			List.of()
		);
		RecipePreferenceIngredientInfo glass = itemWithTag("minecraft:glass", "c:glass_blocks");

		Optional<FocusedRecipe> selected = new RecipePreferenceRules(List.of(rule)).resolvePreferredRecipeForSlot(
			List.of(glass),
			List.of(candidate("test:glass_recipe"), candidate("test:stained_recipe"))
		);

		Assertions.assertTrue(selected.isEmpty());
	}

	private static RecipePreferenceRule rule(
		List<List<RecipePreferenceTarget>> inputTiers,
		List<List<String>> recipeTiers
	) {
		return new RecipePreferenceRule("test", selector("gtceu:lv_circuit"), Optional.of(ASSEMBLER), inputTiers, recipeTiers);
	}

	private static RecipePreferenceTarget selector(String value) {
		return RecipePreferenceTarget.parse(value).orElseThrow();
	}

	private static RecipePreferenceCandidate candidate(String recipeUid, RecipePreferenceIngredientInfo... inputs) {
		return new RecipePreferenceCandidate(new FocusedRecipe(ASSEMBLER, ResourceLocation.parse(recipeUid)), List.of(inputs));
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

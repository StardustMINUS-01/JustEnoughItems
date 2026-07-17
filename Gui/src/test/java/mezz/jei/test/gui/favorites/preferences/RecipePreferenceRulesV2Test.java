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

	private static RecipePreferenceIngredientInfo fluid(String id) {
		return RecipePreferenceIngredientInfo.fluid(ResourceLocation.parse(id), Set.of());
	}
}

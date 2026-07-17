package mezz.jei.test.gui.favorites.preferences;

import mezz.jei.gui.favorites.preferences.RecipePreferenceConfigSerializer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class RecipePreferenceConfigSerializerTest {
	@Test
	public void parsesTwoDimensionalInputAndRecipeTiers() {
		List<String> lines = List.of(
			"[[rules]]",
			"name = \"GTM fine wires\"",
			"target = \"#c:fine_wires\"",
			"recipe_type = \"gtceu:wiremill\"",
			"input = [[\"item:gtceu:polybenzimidazole_foil\", \"fluid:gtceu:rubber\"], [\"fluid:gtceu:latex\"]]",
			"recipe = [",
			"  [\"gtceu:wiremill/mill_*_wire_fine\"],",
			"  [\"gtceu:wiremill/mill_*_wire_to_fine_wire\"]",
			"]",
			"fallback = \"default\""
		);

		var rules = RecipePreferenceConfigSerializer.deserialize(lines);

		Assertions.assertEquals(1, rules.size());
		var rule = rules.getFirst();
		Assertions.assertEquals("GTM fine wires", rule.name());
		Assertions.assertEquals(ResourceLocation.fromNamespaceAndPath("c", "fine_wires"), rule.target().tagId().orElseThrow());
		Assertions.assertEquals(ResourceLocation.fromNamespaceAndPath("gtceu", "wiremill"), rule.recipeType().orElseThrow());
		Assertions.assertEquals(2, rule.inputTiers().size());
		Assertions.assertEquals(2, rule.inputTiers().getFirst().size());
		Assertions.assertEquals(List.of(
			List.of("gtceu:wiremill/mill_*_wire_fine"),
			List.of("gtceu:wiremill/mill_*_wire_to_fine_wire")
		), rule.recipeTiers());
	}

	@Test
	public void skipsInvalidRulesInsteadOfThrowing() {
		List<String> lines = List.of(
			"[[rules]]",
			"name = \"Missing target\"",
			"recipe_type = \"gtceu:wiremill\"",
			"recipe = [[\"gtceu:wiremill/mill_*_wire_fine\"]]",
			"",
			"[[rules]]",
			"name = \"Valid\"",
			"target = \"test:cobalt_fine_wire\"",
			"recipe = [[\"test:recipe\"]]"
		);

		var rules = RecipePreferenceConfigSerializer.deserialize(lines);

		Assertions.assertEquals(1, rules.size());
		Assertions.assertEquals("Valid", rules.getFirst().name());
	}

	@Test
	public void ignoresLegacyFallbackKey() {
		List<String> lines = List.of(
			"[[rules]]",
			"target = \"test:cobalt_fine_wire\"",
			"recipe = [[\"test:recipe\"]]",
			"fallback = \"custom\""
		);

		var rules = RecipePreferenceConfigSerializer.deserialize(lines);

		Assertions.assertEquals(1, rules.size());
	}
}

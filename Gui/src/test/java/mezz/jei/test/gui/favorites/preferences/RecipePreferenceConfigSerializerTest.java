package mezz.jei.test.gui.favorites.preferences;

import mezz.jei.gui.favorites.preferences.RecipePreferenceConfigSerializer;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class RecipePreferenceConfigSerializerTest {
	@Test
	public void outputStartsNewRuleAndInputRecipeAttach() {
		List<String> lines = List.of(
			"output = #c:fine_wires",
			"input = gtceu:iron & gtceu:gold; #c:plates",
			"recipe = gtceu:wiremill/mill_*_wire_fine",
			"output = minecraft:iron_ingot",
			"input = minecraft:iron_ore"
		);
		List<RecipePreferenceRule> rules = RecipePreferenceConfigSerializer.deserialize(lines);
		Assertions.assertEquals(2, rules.size());
		Assertions.assertTrue(rules.get(0).input().isPresent());
		Assertions.assertTrue(rules.get(0).recipe().isPresent());
		Assertions.assertTrue(rules.get(1).input().isPresent());
		Assertions.assertTrue(rules.get(1).recipe().isEmpty());
	}

	@Test
	public void legacySectionsAndNameAreSkippedAsUnknownLines() {
		List<String> lines = List.of(
			"[[rules]]",
			"name = GTM fine wires",
			"output = #c:fine_wires",
			"input = gtceu:iron"
		);
		List<RecipePreferenceRule> rules = RecipePreferenceConfigSerializer.deserialize(lines);
		Assertions.assertEquals(1, rules.size());
	}

	@Test
	public void duplicateInputInvalidatesRule() {
		List<String> lines = List.of(
			"output = #c:fine_wires",
			"input = gtceu:iron",
			"input = gtceu:gold"
		);
		List<RecipePreferenceRule> rules = RecipePreferenceConfigSerializer.deserialize(lines);
		Assertions.assertTrue(rules.isEmpty());
	}

	@Test
	public void ruleWithoutOutputIsSkipped() {
		List<String> lines = List.of("input = gtceu:iron");
		List<RecipePreferenceRule> rules = RecipePreferenceConfigSerializer.deserialize(lines);
		Assertions.assertTrue(rules.isEmpty());
	}

	@Test
	public void multiLineExpressionsAreFolded() {
		List<String> lines = List.of(
			"output = #c:fine_wires",
			"input =",
			"  gtceu:iron & gtceu:gold;",
			"  #c:plates"
		);
		List<RecipePreferenceRule> rules = RecipePreferenceConfigSerializer.deserialize(lines);
		Assertions.assertEquals(1, rules.size());
		Assertions.assertTrue(rules.get(0).input().isPresent());
	}
}

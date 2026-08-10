package mezz.jei.test.gui.favorites.preferences;

import mezz.jei.gui.favorites.preferences.RecipePreferenceConfigSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class RecipePreferenceConfigSerializerTest {
	@Test
	public void parsesMultiLineExpressionsWithComments() {
		List<String> lines = List.of(
			"[[rules]]",
			"name = GTM fine wires",
			"output = #c:fine_wires",
			"input =",
			"  gtceu:iron & gtceu:gold;",
			"  #c:plates $ trailing line comment",
			"recipe =",
			"  gtceu:wiremill/mill_*_wire_fine;",
			"  gtceu:wiremill/mill_*_wire_to_fine_wire"
		);

		var rules = RecipePreferenceConfigSerializer.deserialize(lines);

		Assertions.assertEquals(1, rules.size());
		var rule = rules.getFirst();
		Assertions.assertEquals("GTM fine wires", rule.name());
		Assertions.assertTrue(rule.input().isPresent());
		Assertions.assertTrue(rule.recipe().isPresent());
	}

	@Test
	public void blockCommentsAreRemoved() {
		List<String> lines = List.of(
			"$$ block comment start",
			"output = \"ignored\"",
			"$$ block comment end",
			"[[rules]]",
			"output = minecraft:iron_ingot",
			"input = minecraft:iron_ore"
		);

		var rules = RecipePreferenceConfigSerializer.deserialize(lines);

		Assertions.assertEquals(1, rules.size());
	}

	@Test
	public void skipsRulesWithLegacyKeys() {
		List<String> lines = List.of(
			"[[rules]]",
			"name = Legacy",
			"target = \"#c:fine_wires\"",
			"recipe_type = \"gtceu:wiremill\"",
			"recipe = \"gtceu:wiremill/mill_*_wire_fine\""
		);

		var rules = RecipePreferenceConfigSerializer.deserialize(lines);

		Assertions.assertTrue(rules.isEmpty());
	}

	@Test
	public void skipsRuleMissingOutput() {
		List<String> lines = List.of(
			"[[rules]]",
			"name = Missing output",
			"input = gtceu:iron"
		);

		var rules = RecipePreferenceConfigSerializer.deserialize(lines);

		Assertions.assertTrue(rules.isEmpty());
	}

	@Test
	public void skipsRuleWithoutInputOrRecipe() {
		List<String> lines = List.of(
			"[[rules]]",
			"output = minecraft:iron_ingot"
		);

		var rules = RecipePreferenceConfigSerializer.deserialize(lines);

		Assertions.assertTrue(rules.isEmpty());
	}

	@Test
	public void skipsDuplicateOutput() {
		List<String> lines = List.of(
			"[[rules]]",
			"output = minecraft:iron_ingot",
			"output = minecraft:gold_ingot",
			"input = minecraft:iron_ore"
		);

		var rules = RecipePreferenceConfigSerializer.deserialize(lines);

		Assertions.assertTrue(rules.isEmpty());
	}

	@Test
	public void skipsInvalidExpressions() {
		List<String> lines = List.of(
			"[[rules]]",
			"output = minecraft:iron_ingot",
			"input = gtceu:iron &",
			"",
			"[[rules]]",
			"output = item:*:iron_ingot",
			"input = gtceu:iron"
		);

		var rules = RecipePreferenceConfigSerializer.deserialize(lines);

		Assertions.assertTrue(rules.isEmpty());
	}

	@Test
	public void defaultsNameWhenMissing() {
		List<String> lines = List.of(
			"[[rules]]",
			"output = minecraft:iron_ingot",
			"input = minecraft:iron_ore"
		);

		var rules = RecipePreferenceConfigSerializer.deserialize(lines);

		Assertions.assertEquals(1, rules.size());
		Assertions.assertEquals("unnamed", rules.getFirst().name());
	}

	@Test
	public void skipsQuotedExpressions() {
		List<String> lines = List.of(
			"[[rules]]",
			"name = \"quoted\"",
			"output = minecraft:iron_ingot",
			"input = minecraft:iron_ore"
		);

		var rules = RecipePreferenceConfigSerializer.deserialize(lines);

		Assertions.assertTrue(rules.isEmpty());
	}
}

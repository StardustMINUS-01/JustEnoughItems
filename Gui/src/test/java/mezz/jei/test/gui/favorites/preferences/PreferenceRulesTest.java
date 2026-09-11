package mezz.jei.test.gui.favorites.preferences;

import mezz.jei.gui.favorites.preferences.RecipePreferenceConfigSerializer;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRule;
import mezz.jei.gui.match.IngredientMatchInfo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class PreferenceRulesTest {
	@Test
	public void parsesRuleBoundaries() {
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
		Assertions.assertTrue(rules.get(0).recipe().orElseThrow().rank(ResourceLocation.parse("gtceu:wiremill/mill_iron_wire_fine")).isPresent());
		Assertions.assertTrue(rules.get(1).input().orElseThrow().matches(item("minecraft:iron_ore")));
		Assertions.assertTrue(rules.get(1).output().matches(item("minecraft:iron_ingot")));
		Assertions.assertFalse(rules.get(1).output().matches(item("minecraft:gold_ingot")));
		Assertions.assertTrue(rules.get(1).recipe().isEmpty());
	}

	@Test
	public void ignoresUnknownEntries() {
		List<String> lines = List.of(
			"[[rules]]",
			"name = GTM fine wires",
			"output = #c:fine_wires",
			"input = gtceu:iron"
		);
		List<RecipePreferenceRule> rules = RecipePreferenceConfigSerializer.deserialize(lines);
		Assertions.assertEquals(1, rules.size());
		Assertions.assertTrue(rules.getFirst().input().orElseThrow().matches(item("gtceu:iron")));
	}

	@Test
	public void rejectsDuplicateInput() {
		List<String> lines = List.of(
			"output = #c:fine_wires",
			"input = gtceu:iron",
			"input = gtceu:gold"
		);
		List<RecipePreferenceRule> rules = RecipePreferenceConfigSerializer.deserialize(lines);
		Assertions.assertTrue(rules.isEmpty());
	}

	@Test
	public void requiresOutput() {
		List<String> lines = List.of("input = gtceu:iron");
		List<RecipePreferenceRule> rules = RecipePreferenceConfigSerializer.deserialize(lines);
		Assertions.assertTrue(rules.isEmpty());
	}

	@Test
	public void parsesMultilineInput() {
		List<String> lines = List.of(
			"output = #c:fine_wires",
			"input =",
			"  gtceu:iron & gtceu:gold;",
			"  #c:plates"
		);
		List<RecipePreferenceRule> rules = RecipePreferenceConfigSerializer.deserialize(lines);
		Assertions.assertEquals(1, rules.size());
		var input = rules.getFirst().input().orElseThrow();
		Assertions.assertEquals(0, input.rank(List.of(item("gtceu:iron"), item("gtceu:gold"))).orElseThrow());
		Assertions.assertEquals(1, input.rank(List.of(
			IngredientMatchInfo.item(ResourceLocation.parse("gtceu:plate"), Set.of(ResourceLocation.parse("c:plates")))
		)).orElseThrow());
		Assertions.assertTrue(input.rank(List.of(item("minecraft:dirt"))).isEmpty());
	}

	private static IngredientMatchInfo item(String uid) {
		return IngredientMatchInfo.item(ResourceLocation.parse(uid), Set.of());
	}
}

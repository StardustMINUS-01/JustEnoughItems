package mezz.jei.test.gui.collapsible;

import mezz.jei.gui.collapsible.CollapsibleRules;
import mezz.jei.gui.collapsible.CollapsibleRulesSerializer;
import mezz.jei.gui.collapsible.CollapsibleSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class CollapsibleRulesSerializerTest {
	@Test
	public void eachItemLineIsOneGroupInOrder() {
		List<String> lines = List.of(
			"item = minecraft:potion",
			"item = minecraft:splash_potion"
		);
		CollapsibleRules rules = CollapsibleRulesSerializer.deserialize(lines).rules();
		Assertions.assertEquals(2, rules.groups().size());
		Assertions.assertEquals("minecraft:potion", rules.groups().get(0).expressionText());
		Assertions.assertEquals("minecraft:splash_potion", rules.groups().get(1).expressionText());
	}

	@Test
	public void orInsideOneLineIsOneGroup() {
		List<String> lines = List.of("item = minecraft:potion | minecraft:splash_potion");
		CollapsibleRules rules = CollapsibleRulesSerializer.deserialize(lines).rules();
		Assertions.assertEquals(1, rules.groups().size());
	}

	@Test
	public void unknownKeysAndSectionsAreSkipped() {
		List<String> lines = List.of(
			"[[group]]",
			"name = Example",
			"item = minecraft:potion",
			"typo = minecraft:dirt"
		);
		CollapsibleRules rules = CollapsibleRulesSerializer.deserialize(lines).rules();
		Assertions.assertEquals(1, rules.groups().size());
	}

	@Test
	public void invalidExpressionSkipsGroup() {
		List<String> lines = List.of("item = foo::bar", "item = minecraft:potion");
		CollapsibleRules rules = CollapsibleRulesSerializer.deserialize(lines).rules();
		Assertions.assertEquals(1, rules.groups().size());
	}

	@Test
	public void multiLineValueIsOneGroup() {
		List<String> lines = List.of(
			"item =",
			"  minecraft:potion | minecraft:splash_potion"
		);
		CollapsibleRules rules = CollapsibleRulesSerializer.deserialize(lines).rules();
		Assertions.assertEquals(1, rules.groups().size());
	}

	@Test
	public void settingsUseNeiDefaultsAndParseHex() {
		Assertions.assertEquals(CollapsibleSettings.DEFAULT, CollapsibleRulesSerializer.deserialize(List.of()).settings());
		List<String> lines = List.of(
			"collapsedColor = 0x335555EE",
			"item = minecraft:potion",
			"expandedColor = 0x11223344"
		);
		var loaded = CollapsibleRulesSerializer.deserialize(lines);
		Assertions.assertEquals("minecraft:potion", loaded.rules().groups().getFirst().expressionText());
		CollapsibleSettings settings = loaded.settings();
		Assertions.assertEquals(0x335555EE, settings.collapsedColor());
		Assertions.assertEquals(0x11223344, settings.expandedColor());
	}

	@Test
	public void invalidColorFallsBackToDefault() {
		List<String> lines = List.of("collapsedColor = not-a-color");
		CollapsibleSettings settings = CollapsibleRulesSerializer.deserialize(lines).settings();
		Assertions.assertEquals(CollapsibleSettings.DEFAULT_COLLAPSED_COLOR, settings.collapsedColor());
		Assertions.assertEquals(CollapsibleSettings.DEFAULT_EXPANDED_COLOR, settings.expandedColor());
	}
}

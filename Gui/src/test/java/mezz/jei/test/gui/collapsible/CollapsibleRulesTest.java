package mezz.jei.test.gui.collapsible;

import mezz.jei.gui.collapsible.CollapsibleGroup;
import mezz.jei.gui.collapsible.CollapsibleRulesSerializer;
import mezz.jei.gui.collapsible.CollapsibleSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

public class CollapsibleRulesTest {
	@Test
	public void preservesGroupOrder() {
		Assertions.assertEquals(List.of("minecraft:potion", "minecraft:splash_potion"),
			expressions(List.of("item = minecraft:potion", "item = minecraft:splash_potion")));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"item = minecraft:potion | minecraft:splash_potion",
		"item =\n  minecraft:potion | minecraft:splash_potion"
	})
	public void parsesCombinedGroup(String text) {
		Assertions.assertEquals(List.of("minecraft:potion | minecraft:splash_potion"), expressions(text.lines().toList()));
	}

	@Test
	public void skipsUnknownEntries() {
		Assertions.assertEquals(List.of("minecraft:potion"),
			expressions(List.of("[[group]]", "name = Example", "item = minecraft:potion", "typo = minecraft:dirt")));
	}

	@Test
	public void skipsInvalidExpressions() {
		Assertions.assertEquals(List.of("minecraft:potion"),
			expressions(List.of("item = foo::bar", "item = minecraft:potion")));
	}

	@Test
	public void parsesSettings() {
		Assertions.assertEquals(CollapsibleSettings.DEFAULT, CollapsibleRulesSerializer.deserialize(List.of()).settings());
		var loaded = CollapsibleRulesSerializer.deserialize(List.of(
			"collapsedColor = 0x335555EE", "item = minecraft:potion", "expandedColor = 0x11223344"));
		Assertions.assertEquals("minecraft:potion", loaded.rules().groups().getFirst().expressionText());
		Assertions.assertEquals(0x335555EE, loaded.settings().collapsedColor());
		Assertions.assertEquals(0x11223344, loaded.settings().expandedColor());
	}

	@Test
	public void keepsDefaultColors() {
		var settings = CollapsibleRulesSerializer.deserialize(List.of("collapsedColor = not-a-color")).settings();
		Assertions.assertEquals(CollapsibleSettings.DEFAULT_COLLAPSED_COLOR, settings.collapsedColor());
		Assertions.assertEquals(CollapsibleSettings.DEFAULT_EXPANDED_COLOR, settings.expandedColor());
	}

	private static List<String> expressions(List<String> lines) {
		return CollapsibleRulesSerializer.deserialize(lines).rules().groups().stream()
			.map(CollapsibleGroup::expressionText).toList();
	}
}

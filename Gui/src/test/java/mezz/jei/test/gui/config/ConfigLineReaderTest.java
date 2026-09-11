package mezz.jei.test.gui.config;

import mezz.jei.gui.config.ConfigLineReader;
import mezz.jei.gui.config.ConfigLineReader.Entry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ConfigLineReaderTest {
	@Test
	public void stripsComments() {
		List<String> lines = List.of(
			"$ line comment",
			"output = minecraft:iron_ingot $ trailing",
			"$$ block",
			"$$ end",
			"input = \"minecraft:iron_ore\""
		);
		Assertions.assertEquals(List.of(
			new Entry("output", "minecraft:iron_ingot"),
			new Entry("input", "\"minecraft:iron_ore\"")
		), ConfigLineReader.read(lines));
	}

	@Test
	public void joinsMultilineValues() {
		List<String> lines = List.of(
			"output =",
			"  gtceu:iron & gtceu:gold;",
			"  #c:plates",
			"[[rules]]",
			"output = minecraft:oak_planks"
		);
		Assertions.assertEquals(List.of(
			new Entry("output", "gtceu:iron & gtceu:gold;\n#c:plates"),
			new Entry("output", "minecraft:oak_planks")
		), ConfigLineReader.read(lines));
	}

	@Test
	public void skipsLeadingText() {
		List<String> lines = List.of("not a key value line", "item = minecraft:dirt");
		Assertions.assertEquals(List.of(new Entry("item", "minecraft:dirt")), ConfigLineReader.read(lines));
	}
}

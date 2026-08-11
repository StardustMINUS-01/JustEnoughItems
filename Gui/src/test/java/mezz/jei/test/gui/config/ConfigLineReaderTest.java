package mezz.jei.test.gui.config;

import mezz.jei.gui.config.ConfigLineReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ConfigLineReaderTest {
	@Test
	public void stripsLineAndBlockComments() {
		List<String> lines = List.of(
			"$ line comment",
			"output = minecraft:iron_ingot $ trailing",
			"$$ block",
			"$$ end",
			"input = \"minecraft:iron_ore\""
		);
		List<ConfigLineReader.Entry> entries = ConfigLineReader.read(lines);
		Assertions.assertEquals(2, entries.size());
		Assertions.assertEquals("output", entries.get(0).key());
		Assertions.assertEquals("minecraft:iron_ingot", entries.get(0).value());
		Assertions.assertEquals("input", entries.get(1).key());
		Assertions.assertEquals("\"minecraft:iron_ore\"", entries.get(1).value());
	}

	@Test
	public void foldsMultiLineValuesUntilNextKeyOrSection() {
		List<String> lines = List.of(
			"output =",
			"  gtceu:iron & gtceu:gold;",
			"  #c:plates",
			"[[rules]]",
			"output = minecraft:oak_planks"
		);
		List<ConfigLineReader.Entry> entries = ConfigLineReader.read(lines);
		Assertions.assertEquals(2, entries.size());
		Assertions.assertEquals("gtceu:iron & gtceu:gold;\n#c:plates", entries.get(0).value());
		Assertions.assertEquals("output", entries.get(1).key());
		Assertions.assertEquals("minecraft:oak_planks", entries.get(1).value());
	}

	@Test
	public void skipsNonKeyLinesWithoutCurrentKey() {
		List<String> lines = List.of("not a key value line", "item = minecraft:dirt");
		List<ConfigLineReader.Entry> entries = ConfigLineReader.read(lines);
		Assertions.assertEquals(1, entries.size());
		Assertions.assertEquals("item", entries.get(0).key());
	}
}

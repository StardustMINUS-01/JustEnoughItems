package mezz.jei.test.gui.config;

import mezz.jei.gui.config.ConfigFileImporter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ConfigFileImporterTest {
	@TempDir
	Path tempDir;

	@Test
	public void appendsAndDeletesSource() throws Exception {
		Path target = writeFile("recipe-preferences.txt", "output = minecraft:iron_ingot", "input = minecraft:iron_ore");
		Path source = writeFile("recipe-preferences-test.txt", "output = minecraft:gold_ingot", "input = minecraft:gold_ore");

		new ConfigFileImporter(tempDir).importFiles("recipe-preferences-", target);

		Assertions.assertEquals(
			List.of(
				"output = minecraft:iron_ingot",
				"input = minecraft:iron_ore",
				"",
				"output = minecraft:gold_ingot",
				"input = minecraft:gold_ore"
			),
			Files.readAllLines(target)
		);
		Assertions.assertFalse(Files.exists(source));
	}

	@Test
	public void ignoresNonMatchingFiles() throws Exception {
		Path target = writeFile("collapsible-items.txt", "item = minecraft:dirt");
		Path unrelated = writeFile("other.txt", "item = minecraft:stone");

		new ConfigFileImporter(tempDir).importFiles("collapsible-items-", target);

		Assertions.assertEquals(List.of("item = minecraft:dirt"), Files.readAllLines(target));
		Assertions.assertTrue(Files.exists(unrelated));
	}

	@Test
	public void importsInOrder() throws Exception {
		Path target = writeFile("recipe-preferences.txt", "output = minecraft:iron_ingot");
		Path b = writeFile("recipe-preferences-b.txt", "output = minecraft:b");
		Path a = writeFile("recipe-preferences-a.txt", "output = minecraft:a");

		new ConfigFileImporter(tempDir).importFiles("recipe-preferences-", target);

		Assertions.assertEquals(List.of(
			"output = minecraft:iron_ingot", "", "output = minecraft:a", "", "output = minecraft:b"
		), Files.readAllLines(target));
		Assertions.assertFalse(Files.exists(a));
		Assertions.assertFalse(Files.exists(b));
	}

	@Test
	public void removesEmptySource() throws Exception {
		Path target = writeFile("collapsible-items.txt", "item = minecraft:dirt");
		Path empty = writeFile("collapsible-items-empty.txt");

		new ConfigFileImporter(tempDir).importFiles("collapsible-items-", target);

		Assertions.assertEquals(List.of("item = minecraft:dirt"), Files.readAllLines(target));
		Assertions.assertFalse(Files.exists(empty));
	}

	private Path writeFile(String name, String... lines) throws Exception {
		return Files.write(tempDir.resolve(name), List.of(lines));
	}
}

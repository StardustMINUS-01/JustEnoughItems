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
	public void appendsWithBlankSeparatorAndDeletesSource() throws Exception {
		Path target = tempDir.resolve("recipe-preferences.txt");
		Files.write(target, List.of("output = minecraft:iron_ingot", "input = minecraft:iron_ore"));
		Path source = tempDir.resolve("recipe-preferences-test.txt");
		Files.write(source, List.of("output = minecraft:gold_ingot", "input = minecraft:gold_ore"));

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
		Path target = tempDir.resolve("collapsible-items.txt");
		Files.write(target, List.of("item = minecraft:dirt"));
		Path unrelated = tempDir.resolve("other.txt");
		Files.write(unrelated, List.of("item = minecraft:stone"));

		new ConfigFileImporter(tempDir).importFiles("collapsible-items-", target);

		Assertions.assertEquals(List.of("item = minecraft:dirt"), Files.readAllLines(target));
		Assertions.assertTrue(Files.exists(unrelated));
	}

	@Test
	public void importsMultipleFilesInSortedOrder() throws Exception {
		Path target = tempDir.resolve("recipe-preferences.txt");
		Files.write(target, List.of("output = minecraft:iron_ingot"));
		Path b = tempDir.resolve("recipe-preferences-b.txt");
		Files.write(b, List.of("output = minecraft:b"));
		Path a = tempDir.resolve("recipe-preferences-a.txt");
		Files.write(a, List.of("output = minecraft:a"));

		new ConfigFileImporter(tempDir).importFiles("recipe-preferences-", target);

		List<String> lines = Files.readAllLines(target);
		Assertions.assertTrue(lines.contains("output = minecraft:a"));
		Assertions.assertTrue(lines.contains("output = minecraft:b"));
		Assertions.assertFalse(Files.exists(a));
		Assertions.assertFalse(Files.exists(b));
		Assertions.assertTrue(lines.indexOf("output = minecraft:a") < lines.indexOf("output = minecraft:b"));
	}

	@Test
	public void skipsEmptySourceFiles() throws Exception {
		Path target = tempDir.resolve("collapsible-items.txt");
		Files.write(target, List.of("item = minecraft:dirt"));
		Path empty = tempDir.resolve("collapsible-items-empty.txt");
		Files.write(empty, List.of());

		new ConfigFileImporter(tempDir).importFiles("collapsible-items-", target);

		Assertions.assertEquals(List.of("item = minecraft:dirt"), Files.readAllLines(target));
		Assertions.assertFalse(Files.exists(empty));
	}
}

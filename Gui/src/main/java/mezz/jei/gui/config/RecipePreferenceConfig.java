package mezz.jei.gui.config;

import mezz.jei.gui.favorites.preferences.RecipePreferenceConfigSerializer;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RecipePreferenceConfig {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String FILE_NAME = "recipe-preferences.txt";

	private final Path path;

	public RecipePreferenceConfig(Path jeiConfigurationDir) {
		this.path = jeiConfigurationDir.resolve(FILE_NAME);
	}

	public RecipePreferenceRules loadRules() {
		ensureDefaultFile();
		try {
			return new RecipePreferenceRules(RecipePreferenceConfigSerializer.deserialize(Files.readAllLines(path)));
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Failed to load recipe preference rules from file {}", path, e);
			return RecipePreferenceRules.EMPTY;
		}
	}

	public void ensureDefaultFile() {
		if (!Files.exists(path)) {
			writeDefaultFile();
		}
	}

	public Path getPath() {
		return path;
	}

	private void writeDefaultFile() {
		List<String> lines = List.of(
			"$ JEI recipe preference rules.",
			"$ every output = start a new rule; input / recipe belongs to the nearest output.",
			"$ output / input / recipe are boolean expressions:",
			"$   ! not, & and, | or, ( ) grouping, ; priority (first = highest)",
			"$ Selectors: item:id, fluid:id, id, #tag, wildcards like gtceu:*_wire or *:path;",
			"$ tag wildcards like #*:ingots.",
			"$ A value spans lines until the next \"key =\".",
			"$ \"$\" starts a line comment; \"$$ ... $$\" starts a block comment.",
			"$ Rules are checked in order; a rule must uniquely select one recipe.",
			"$ Quick import: drop files named \"recipe-preferences-*.txt\" into this folder;",
			"$ their contents are appended to this file and the files are deleted automatically.",
			"",
			"$$",
			"output = #c:fine_wires",
			"input =",
			"  gtceu:iron & gtceu:gold;",
			"  #c:plates",
			"recipe =",
			"  gtceu:wiremill/mill_*_wire_fine;",
			"  gtceu:wiremill/mill_*_wire_to_fine_wire",
			"$$",
			"",
			"$$",
			"output =",
			"  minecraft:*planks & gtceu:*wire",
			"input =",
			"  #c:logs",
			"$$"
		);
		try {
			Files.createDirectories(path.getParent());
			Files.write(path, lines);
		} catch (IOException e) {
			LOGGER.error("Failed to create default recipe preference config at {}", path, e);
		}
	}
}

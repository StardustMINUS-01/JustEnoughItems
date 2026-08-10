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
		if (!Files.exists(path)) {
			writeDefaultFile();
			return RecipePreferenceRules.EMPTY;
		}

		try {
			return new RecipePreferenceRules(RecipePreferenceConfigSerializer.deserialize(Files.readAllLines(path)));
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Failed to load recipe preference rules from file {}", path, e);
			return RecipePreferenceRules.EMPTY;
		}
	}

	public Path getPath() {
		return path;
	}

	private void writeDefaultFile() {
		List<String> lines = List.of(
			"$ JEI recipe preference rules.",
			"$ output / input / recipe are boolean expressions:",
			"$   ! not, & and, | or, ( ) grouping, ; priority (first = highest)",
			"$ Selectors: item:id, fluid:id, id, #tag, path wildcards like gtceu:*_wire.",
			"$ A value spans lines until the next \"key =\" or \"[[rules]]\".",
			"$ \"$\" starts a line comment; \"$$ ... $$\" starts a block comment.",
			"$ Rules are checked in order; a rule must uniquely select one recipe.",
			"",
			"$ [[rules]]",
			"$ name = GTM fine wires",
			"$ output = #c:fine_wires",
			"$ input =",
			"$   gtceu:iron & gtceu:gold;",
			"$   #c:plates",
			"$ recipe =",
			"$   gtceu:wiremill/mill_*_wire_fine;",
			"$   gtceu:wiremill/mill_*_wire_to_fine_wire",
			"",
			"$ [[rules]]",
			"$ name = Planks and wire",
			"$ output =",
			"$   minecraft:*planks & gtceu:*wire",
			"$ input =",
			"$   #c:logs"
		);
		try {
			Files.createDirectories(path.getParent());
			Files.write(path, lines);
		} catch (IOException e) {
			LOGGER.error("Failed to create default recipe preference config at {}", path, e);
		}
	}
}

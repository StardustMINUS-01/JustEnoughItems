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
	private static final String FILE_NAME = "recipe-preferences.toml";

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
			"# JEI recipe preference rules.",
			"# These client-side rules help Shift+A and Shift+F choose a recipe when an ingredient has multiple recipe paths.",
			"# target and input selectors accept item:/fluid: prefixes, tags, and path wildcards such as gtceu:*_single_wire.",
			"# input and recipe are two-dimensional arrays: outer rows are priority tiers, input rows are AND, and recipe rows are OR.",
			"",
			"# [[rules]]",
			"# name = \"GTM fine wires\"",
			"# target = \"#c:fine_wires\"",
			"# recipe_type = \"gtceu:wiremill\"",
			"# recipe = [",
			"#   [\"gtceu:wiremill/mill_*_wire_fine\"],",
			"#   [\"gtceu:wiremill/mill_*_wire_to_fine_wire\"]",
			"# ]"
		);
		try {
			Files.createDirectories(path.getParent());
			Files.write(path, lines);
		} catch (IOException e) {
			LOGGER.error("Failed to create default recipe preference config at {}", path, e);
		}
	}
}

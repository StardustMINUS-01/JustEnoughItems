package mezz.jei.test.gui.config;

import mezz.jei.gui.config.RecipePreferenceConfig;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

public class RecipePreferenceConfigTest {
	@TempDir
	Path tempDir;

	@Test
	public void createsDefaultExampleFile() {
		RecipePreferenceConfig config = new RecipePreferenceConfig(tempDir);
		RecipePreferenceRules rules = config.loadRules();
		Assertions.assertTrue(Files.exists(config.getPath()));
		// The default template keeps both example rules inside $$ block comments,
		// so no rules are active until the player edits the file.
		Assertions.assertTrue(rules.isEmpty());
	}
}

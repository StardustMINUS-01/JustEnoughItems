package mezz.jei.test.gui.config;

import mezz.jei.gui.collapsible.CollapsibleRules;
import mezz.jei.gui.collapsible.CollapsibleSettings;
import mezz.jei.gui.config.CollapsibleConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

public class CollapsibleConfigTest {
	@TempDir
	Path tempDir;

	@Test
	public void createsDefaultFileAndParsesRules() {
		CollapsibleConfig config = new CollapsibleConfig(tempDir);
		CollapsibleRules rules = config.loadRules();
		Assertions.assertTrue(Files.exists(config.getPath()));
		Assertions.assertFalse(rules.isEmpty());
		Assertions.assertTrue(rules.groups().size() > 50);
		Assertions.assertEquals(
			"minecraft:*_log & !minecraft:stripped_*_log",
			rules.groups().getFirst().expressionText()
		);
	}

	@Test
	public void defaultSettingsMatchNeiColors() {
		CollapsibleConfig config = new CollapsibleConfig(tempDir);
		config.loadRules();
		Assertions.assertEquals(CollapsibleSettings.DEFAULT, config.loadSettings());
	}
}

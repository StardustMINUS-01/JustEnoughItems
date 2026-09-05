package mezz.jei.test.gui.config;

import mezz.jei.gui.collapsible.CollapsibleRules;
import mezz.jei.gui.collapsible.CollapsibleSettings;
import mezz.jei.gui.config.CollapsibleConfig;
import mezz.jei.gui.config.ConfigRulesReloadController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

	@Test
	public void coalescesFileChangesUntilTheClientThreadReloadRuns() {
		AtomicInteger loadCount = new AtomicInteger();
		CollapsibleRules expectedRules = CollapsibleRules.EMPTY;
		List<Runnable> clientTasks = new ArrayList<>();
		List<CollapsibleRules> appliedRules = new ArrayList<>();
		ConfigRulesReloadController<CollapsibleRules> controller = new ConfigRulesReloadController<>(
			() -> {
				loadCount.incrementAndGet();
				return expectedRules;
			},
			clientTasks::add,
			appliedRules::add
		);

		controller.onConfigFileChanged();
		controller.onConfigFileChanged();

		Assertions.assertEquals(0, loadCount.get());
		Assertions.assertEquals(1, clientTasks.size());

		clientTasks.removeFirst().run();

		Assertions.assertEquals(1, loadCount.get());
		Assertions.assertEquals(List.of(expectedRules), appliedRules);

		controller.onConfigFileChanged();
		Assertions.assertEquals(1, clientTasks.size());
	}
}

package mezz.jei.test.gui.config;

import mezz.jei.gui.config.RecipePreferenceConfig;
import mezz.jei.gui.config.RecipePreferenceRulesReloadController;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

	@Test
	public void coalescesFileChangesUntilTheClientThreadReloadRuns() {
		AtomicInteger loadCount = new AtomicInteger();
		RecipePreferenceRules expectedRules = RecipePreferenceRules.EMPTY;
		List<Runnable> clientTasks = new ArrayList<>();
		List<RecipePreferenceRules> appliedRules = new ArrayList<>();
		RecipePreferenceRulesReloadController controller = new RecipePreferenceRulesReloadController(
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

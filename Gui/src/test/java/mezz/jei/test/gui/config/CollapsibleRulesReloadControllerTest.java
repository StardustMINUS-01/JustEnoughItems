package mezz.jei.test.gui.config;

import mezz.jei.gui.config.CollapsibleRulesReloadController;
import mezz.jei.gui.collapsible.CollapsibleRules;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CollapsibleRulesReloadControllerTest {
	@Test
	public void coalescesFileChangesUntilTheClientThreadReloadRuns() {
		AtomicInteger loadCount = new AtomicInteger();
		CollapsibleRules expectedRules = CollapsibleRules.EMPTY;
		List<Runnable> clientTasks = new ArrayList<>();
		List<CollapsibleRules> appliedRules = new ArrayList<>();
		CollapsibleRulesReloadController controller = new CollapsibleRulesReloadController(
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

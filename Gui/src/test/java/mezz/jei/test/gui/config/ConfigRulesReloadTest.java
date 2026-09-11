package mezz.jei.test.gui.config;

import mezz.jei.gui.config.ConfigRulesReloadController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ConfigRulesReloadTest {
	@Test
	public void coalescesReloads() {
		AtomicInteger loads = new AtomicInteger();
		List<Runnable> tasks = new ArrayList<>();
		List<Integer> applied = new ArrayList<>();
		ConfigRulesReloadController<Integer> controller = new ConfigRulesReloadController<>(
			loads::incrementAndGet, tasks::add, applied::add);

		controller.onConfigFileChanged();
		controller.onConfigFileChanged();
		Assertions.assertEquals(0, loads.get());
		Assertions.assertTrue(applied.isEmpty());
		Assertions.assertEquals(1, tasks.size());

		tasks.removeFirst().run();
		Assertions.assertEquals(1, loads.get());
		Assertions.assertEquals(List.of(1), applied);

		controller.onConfigFileChanged();
		Assertions.assertEquals(1, tasks.size());
		tasks.removeFirst().run();
		Assertions.assertEquals(2, loads.get());
		Assertions.assertEquals(List.of(1, 2), applied);
	}
}

package mezz.jei.test.gui.config;

import mezz.jei.gui.collapsible.CollapsibleRules;
import mezz.jei.gui.collapsible.CollapsibleSettings;
import mezz.jei.gui.config.CollapsibleConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CollapsibleConfigTest {
	@Test
	public void reloadsRulesSelectively() throws Exception {
		CollapsibleConfig config = new CollapsibleConfig(tempDir);
		Files.write(config.getPath(), List.of("item = minecraft:potion", "item = minecraft:*"));
		var loaded = config.load();
		var state = new mezz.jei.gui.collapsible.CollapsibleState();
		var manager = new mezz.jei.gui.collapsible.CollapsibleManager(loaded.rules(), loaded.settings(), state);
		var first = loaded.rules().groups().getFirst();
		state.setExpanded(first.id(), true);
		AtomicInteger notices = new AtomicInteger();
		manager.addRulesChangedListener(notices::incrementAndGet);
		Files.write(config.getPath(), List.of("$ Changed comment", "item = minecraft:potion", "item = minecraft:*", "expandedColor = 0x11223344"));
		loaded = config.load();
		var original = manager.rules();
		manager.reload(loaded.rules(), loaded.settings());
		Assertions.assertSame(original, manager.rules());
		Assertions.assertEquals(0x11223344, manager.settings().expandedColor());
		Assertions.assertTrue(state.isExpanded(first.id()));
		Assertions.assertEquals(0, notices.get());
		Files.write(config.getPath(), List.of("item = minecraft:*", "item = minecraft:potion"));
		loaded = config.load();
		manager.reload(loaded.rules(), loaded.settings());
		Assertions.assertSame(loaded.rules(), manager.rules());
		Assertions.assertEquals("minecraft:*", manager.rules().groups().getFirst().expressionText());
		Assertions.assertEquals(1, notices.get());
		Files.write(config.getPath(), List.of("item = minecraft:*"));
		loaded = config.load();
		manager.reload(loaded.rules(), loaded.settings());
		Assertions.assertFalse(state.isExpanded(first.id()));
		Assertions.assertEquals(2, notices.get());
	}

	@TempDir
	Path tempDir;

	@Test
	public void loadsDefaultRules() {
		CollapsibleConfig config = new CollapsibleConfig(tempDir);
		var loaded = config.load();
		CollapsibleRules rules = loaded.rules();
		Assertions.assertTrue(Files.exists(config.getPath()));
		Assertions.assertFalse(rules.isEmpty());
		Assertions.assertEquals(CollapsibleSettings.DEFAULT, loaded.settings());
		Assertions.assertEquals(
			"minecraft:*_log & !minecraft:stripped_*_log",
			rules.groups().getFirst().expressionText()
		);
	}
}

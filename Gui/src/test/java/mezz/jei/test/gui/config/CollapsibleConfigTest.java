package mezz.jei.test.gui.config;

import mezz.jei.gui.collapsible.CollapsibleRules;
import mezz.jei.gui.collapsible.CollapsibleSettings;
import mezz.jei.gui.collapsible.CollapsibleGroup;
import mezz.jei.common.config.CollapsibleColorConfig;
import mezz.jei.gui.match.IngredientMatchInfo;
import net.minecraft.resources.ResourceLocation;
import mezz.jei.gui.config.CollapsibleConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class CollapsibleConfigTest {
	@Test
	public void reloadsRulesSelectively() throws Exception {
		CollapsibleConfig config = new CollapsibleConfig(tempDir);
		var loaded = load(config, "item = minecraft:potion", "item = minecraft:*");
		var state = new mezz.jei.gui.collapsible.CollapsibleState();
		var manager = new mezz.jei.gui.collapsible.CollapsibleManager(loaded, CollapsibleSettings.DEFAULT, state);
		var first = loaded.groups().getFirst();
		state.setExpanded(first.id(), true);
		AtomicInteger notices = new AtomicInteger();
		manager.addRulesChangedListener(notices::incrementAndGet);
		var original = manager.rules();
		manager.reload(load(config, "$ Changed comment", "item = minecraft:potion", "item = minecraft:*", "expandedColor = 0x11223344"));
		Assertions.assertEquals(CollapsibleSettings.DEFAULT, manager.settings());
		var serializer = CollapsibleColorConfig.getExpandedColor().getSerializer();
		int color = serializer.deserialize("0xFF123456").getResult().orElseThrow();
		Assertions.assertEquals("0xFF123456", serializer.serialize(color));
		Assertions.assertFalse(serializer.deserialize("0x100000000").getErrors().isEmpty());
		manager.setSettings(new CollapsibleSettings(CollapsibleSettings.DEFAULT_COLLAPSED_COLOR, color));
		Assertions.assertSame(original, manager.rules());
		Assertions.assertEquals(color, manager.settings().expandedColor());
		Assertions.assertTrue(state.isExpanded(first.id()));
		Assertions.assertEquals(0, notices.get());
		loaded = load(config, "item = minecraft:*", "item = minecraft:potion");
		manager.reload(loaded);
		Assertions.assertSame(loaded, manager.rules());
		Assertions.assertEquals("minecraft:*", manager.rules().groups().getFirst().expressionText());
		Assertions.assertEquals(1, notices.get());
		manager.reload(load(config, "item = minecraft:*"));
		Assertions.assertFalse(state.isExpanded(first.id()));
		Assertions.assertEquals(2, notices.get());
	}

	@TempDir
	Path tempDir;

	@Test
	public void initializesEmptyDirectory() throws Exception {
		CollapsibleConfig config = new CollapsibleConfig(tempDir);
		config.initialize();
		Path defaults = config.getDirectory().resolve("collapsible-items-default.txt");
		Assertions.assertTrue(Files.exists(defaults));
		Assertions.assertFalse(config.load().isEmpty());
		Files.delete(defaults);
		Assertions.assertTrue(config.load().isEmpty());
		Assertions.assertFalse(Files.exists(defaults));
		Files.createFile(config.getDirectory().resolve("empty.txt"));
		config.initialize();
		Assertions.assertTrue(config.load().isEmpty());
		Assertions.assertFalse(Files.exists(defaults));
	}

	@Test
	public void loadsFilesInOrder() throws Exception {
		CollapsibleConfig config = new CollapsibleConfig(tempDir);
		Files.createDirectories(config.getDirectory());
		Path a = config.getDirectory().resolve("a.txt");
		Path priority = config.getDirectory().resolve("[pack]rules.txt");
		Files.write(a, List.of("item = minecraft:stone | minecraft:potion", "$$ unfinished comment"));
		Files.write(config.getDirectory().resolve("collapsible-items-default.txt"), List.of("item = minecraft:dirt"));
		Files.write(priority, List.of("item = minecraft:potion"));
		Files.write(config.getDirectory().resolve("ignored.bak"), List.of("item = minecraft:air"));
		var potion = IngredientMatchInfo.item(ResourceLocation.parse("minecraft:potion"), Set.of());
		var stone = IngredientMatchInfo.item(ResourceLocation.parse("minecraft:stone"), Set.of());
		var rules = config.load();
		Assertions.assertEquals(List.of("minecraft:potion", "minecraft:stone | minecraft:potion", "minecraft:dirt"), expressions(rules));
		Assertions.assertEquals(0, rules.resolve(potion));
		Assertions.assertEquals(1, rules.resolve(stone));
		Files.delete(priority);
		rules = config.load();
		Assertions.assertEquals(List.of("minecraft:stone | minecraft:potion", "minecraft:dirt"), expressions(rules));
		Assertions.assertEquals(rules.resolve(stone), rules.resolve(potion));
		Assertions.assertTrue(Files.exists(a));
	}

	@Test
	public void migratesWithoutOverwriting() throws Exception {
		Path legacy = tempDir.resolve("collapsible-items.txt");
		Path extra = tempDir.resolve("collapsible-items-extra.txt");
		Files.write(legacy, List.of("item = minecraft:potion"));
		Files.write(extra, List.of("item = minecraft:dirt"));
		CollapsibleConfig config = new CollapsibleConfig(tempDir);
		config.initialize();
		Assertions.assertFalse(Files.exists(legacy));
		Assertions.assertFalse(Files.exists(extra));
		Assertions.assertEquals(List.of("minecraft:potion", "minecraft:dirt"), expressions(config.load()));
		Files.write(legacy, List.of("item = minecraft:stone"));
		config.initialize();
		Assertions.assertTrue(Files.exists(legacy));
		Assertions.assertEquals(List.of("minecraft:potion", "minecraft:dirt"), expressions(config.load()));
	}

	private static CollapsibleRules load(CollapsibleConfig config, String... lines) throws Exception {
		Files.createDirectories(config.getDirectory());
		Files.write(config.getDirectory().resolve("personal.txt"), List.of(lines));
		return config.load();
	}

	private static List<String> expressions(CollapsibleRules rules) {
		return rules.groups().stream().map(CollapsibleGroup::expressionText).toList();
	}
}

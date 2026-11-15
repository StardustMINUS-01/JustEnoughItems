package mezz.jei.test.gui.lib;

import mezz.jei.common.config.ClientConfigs;
import net.mezzdev.config.file.ConfigFileWatcherSettings;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigSchemaBuilder;

import java.nio.file.Path;
import java.util.Comparator;

public final class ConfigTestFixtures {
	private ConfigTestFixtures() {}

	public static ConfigSchemaBuilder createSchema(Path path) {
		return new ConfigSchemaBuilder("jei", path, "test.config", createManager());
	}

	public static ClientConfigs createClientConfigs(Path path) {
		ConfigManager manager = createManager();
		return new ClientConfigs(
			new ConfigSchemaBuilder("jei", path, "jei.config.client", manager),
			false,
			manager.createInMemorySortingConfig(Comparator.naturalOrder(), true)
		);
	}

	private static ConfigManager createManager() {
		var disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		return new ConfigManager("JEIU Config Test", disabledWatcher, disabledWatcher);
	}
}

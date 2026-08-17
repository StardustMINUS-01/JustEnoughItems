package mezz.jei.gui.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class CollapsibleStateStore {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String FILE_NAME = "collapsible-items-state.json";
	private final Path path;
	private final Gson gson = new Gson();

	public CollapsibleStateStore(Path jeiConfigurationDir) {
		this.path = jeiConfigurationDir.resolve(FILE_NAME);
	}

	public Map<String, Boolean> load() {
		if (!Files.exists(path)) {
			return Map.of();
		}
		try {
			String json = Files.readString(path);
			Map<String, Boolean> parsed = gson.fromJson(json, new TypeToken<Map<String, Boolean>>() {}.getType());
			return parsed == null ? Map.of() : parsed;
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Failed to load collapsible items state from {}", path, e);
			return Map.of();
		}
	}

	public void save(Map<String, Boolean> overrides) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, gson.toJson(overrides));
		} catch (IOException e) {
			LOGGER.error("Failed to save collapsible items state to {}", path, e);
		}
	}
}

package mezz.jei.gui.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Imports drop-in config files named "{prefix}*.txt" from the JEI config
 * directory by appending their contents to the target file and deleting them.
 */
public final class ConfigFileImporter {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String FILE_SUFFIX = ".txt";

	private final Path configDir;

	public ConfigFileImporter(Path configDir) {
		this.configDir = configDir;
	}

	public void importFiles(String prefix, Path target) {
		if (!Files.isDirectory(configDir)) {
			return;
		}
		try (Stream<Path> files = Files.list(configDir)) {
			files.filter(path -> isImportFile(path, prefix))
				.sorted()
				.forEach(path -> importFile(path, target));
		} catch (IOException e) {
			LOGGER.error("Failed to scan config directory {} for import files", configDir, e);
		}
	}

	private boolean isImportFile(Path path, String prefix) {
		String fileName = path.getFileName().toString();
		return fileName.startsWith(prefix) && fileName.endsWith(FILE_SUFFIX);
	}

	private void importFile(Path source, Path target) {
		try {
			List<String> lines = Files.readAllLines(source);
			if (!lines.isEmpty()) {
				List<String> toAppend = new ArrayList<>(lines.size() + 1);
				toAppend.add("");
				toAppend.addAll(lines);
				Files.write(target, toAppend, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
				// Clear the source before deleting it, so a failed delete
				// cannot cause a repeated import on the next scan.
				Files.write(source, List.of());
			}
			Files.deleteIfExists(source);
			LOGGER.info("Imported {} into {}", source.getFileName(), target.getFileName());
		} catch (IOException e) {
			LOGGER.error("Failed to import {} into {}", source, target, e);
		}
	}
}

package mezz.jei.gui.config;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared line reader for fork-owned config files.
 * Handles "$" line comments, "$$ ... $$" block comments, quoted separators,
 * "[[section]]" boundaries and multi-line key = value entries.
 * Unknown non-key lines are skipped.
 */
public final class ConfigLineReader {
	private ConfigLineReader() {}

	public record Entry(String key, String value) {}

	public static List<Entry> read(List<String> lines) {
		List<Entry> entries = new ArrayList<>();
		String stripped = stripComments(String.join("\n", lines));
		String[] strippedLines = stripped.split("\n", -1);
		@Nullable String currentKey = null;
		StringBuilder currentValue = new StringBuilder();
		for (String line : strippedLines) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.startsWith("[[") && trimmed.endsWith("]]")) {
				flush(entries, currentKey, currentValue);
				currentKey = null;
				currentValue = new StringBuilder();
				continue;
			}
			int separator = findSeparator(line);
			if (separator >= 0) {
				flush(entries, currentKey, currentValue);
				currentKey = line.substring(0, separator).trim();
				currentValue = new StringBuilder(line.substring(separator + 1).trim());
			} else if (currentKey != null) {
				if (!currentValue.isEmpty()) {
					currentValue.append('\n');
				}
				currentValue.append(trimmed);
			}
		}
		flush(entries, currentKey, currentValue);
		return entries;
	}

	private static void flush(List<Entry> entries, @Nullable String key, StringBuilder value) {
		if (key != null) {
			entries.add(new Entry(key, value.toString().trim()));
		}
	}

	static String stripComments(String text) {
		StringBuilder result = new StringBuilder();
		boolean inQuotes = false;
		boolean inBlockComment = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (inBlockComment) {
				if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '$') {
					inBlockComment = false;
					i++;
					while (i < text.length() && text.charAt(i) != '\n') {
						i++;
					}
				}
				continue;
			}
			if (c == '"') {
				inQuotes = !inQuotes;
				result.append(c);
				continue;
			}
			if (!inQuotes && c == '$') {
				if (i + 1 < text.length() && text.charAt(i + 1) == '$') {
					inBlockComment = true;
					i++;
				} else {
					while (i < text.length() && text.charAt(i) != '\n') {
						i++;
					}
					result.append('\n');
					continue;
				}
			} else {
				result.append(c);
			}
		}
		return result.toString();
	}

	private static int findSeparator(String line) {
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				inQuotes = !inQuotes;
			} else if (c == '=' && !inQuotes) {
				return i;
			}
		}
		return -1;
	}
}

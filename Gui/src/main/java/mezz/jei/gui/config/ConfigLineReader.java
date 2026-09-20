package mezz.jei.gui.config;

import org.jetbrains.annotations.Nullable;
import mezz.jei.gui.match.ExpressionSyntax;

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
		LineReader reader = new LineReader();
		@Nullable
		String key = null;
		StringBuilder value = new StringBuilder();
		for (int i = 0; i <= lines.size(); i++) {
			if (i == lines.size()) {
				if (reader.text.isEmpty()) {
					break;
				}
				reader.blockComment = false;
			}
			boolean quotedStart = reader.syntax.isQuoted();
			String text = reader.read(i < lines.size() ? lines.get(i) : "");
			if (text == null || text.isBlank() && !quotedStart) {
				continue;
			}
			String trimmed = text.trim();
			boolean section = reader.syntax.isTopLevel() && trimmed.startsWith("[[") && trimmed.endsWith("]]");
			if (section || reader.separator >= 0) {
				if (key != null) {
					entries.add(new Entry(key, value.toString().trim()));
				}
				key = section ? null : text.substring(0, reader.separator).trim();
				value.setLength(0);
				text = section ? "" : text.substring(reader.separator + 1);
			}
			if (key != null) {
				if (!value.isEmpty()) {
					value.append('\n');
				}
				text = quotedStart ? text : text.stripLeading();
				value.append(reader.syntax.isQuoted() ? text : text.stripTrailing());
			} else {
				reader.syntax = new ExpressionSyntax();
			}
		}
		if (key != null) {
			entries.add(new Entry(key, value.toString().trim()));
		}
		return entries;
	}

	private static final class LineReader {
		private ExpressionSyntax syntax = new ExpressionSyntax();
		private final StringBuilder text = new StringBuilder();
		private boolean blockComment;
		private int separator = -1;

		@Nullable
		private String read(String line) {
			if (text.isEmpty()) {
				separator = -1;
			}
			for (int i = 0; i < line.length(); i++) {
				char c = line.charAt(i);
				if (blockComment) {
					if (line.startsWith("$$", i)) {
						blockComment = false;
						return null;
					}
				} else if (c == '$' && !syntax.isQuoted()) {
					blockComment = line.startsWith("$$", i);
					if (blockComment) {
						i++;
					} else {
						break;
					}
				} else {
					if (c == '=' && separator < 0 && syntax.isTopLevel()) {
						separator = text.length();
					}
					syntax.accept(c);
					text.append(c);
				}
			}
			if (blockComment) {
				return null;
			}
			syntax.accept('\n');
			String result = text.toString();
			text.setLength(0);
			return result;
		}
	}
}

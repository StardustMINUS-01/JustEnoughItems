package mezz.jei.gui.favorites.preferences;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RecipePreferenceConfigSerializer {
	private static final Logger LOGGER = LogManager.getLogger();

	private RecipePreferenceConfigSerializer() {
	}

	public static List<RecipePreferenceRule> deserialize(List<String> lines) {
		List<RuleBuilder> builders = new ArrayList<>();
		RuleBuilder current = null;
		List<String> strippedLines = List.of(stripComments(String.join("\n", lines)).split("\n", -1));
		for (int i = 0; i < strippedLines.size(); i++) {
			String line = strippedLines.get(i);
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if ("[[rules]]".equals(trimmed)) {
				current = new RuleBuilder();
				builders.add(current);
				continue;
			}
			if (current == null) {
				continue;
			}
			int separator = findSeparator(line);
			if (separator < 0) {
				current.invalidate("expected key = value");
				continue;
			}
			String key = line.substring(0, separator).trim();
			StringBuilder value = new StringBuilder(line.substring(separator + 1).trim());
			if ("name".equals(key)) {
				int j = i + 1;
				while (j < strippedLines.size()) {
					String next = strippedLines.get(j).trim();
					if (next.isEmpty()) {
						j++;
						continue;
					}
					if (next.equals("[[rules]]") || findSeparator(strippedLines.get(j)) >= 0) {
						break;
					}
					current.invalidate("name must be on a single line");
					break;
				}
			} else {
				int j = i + 1;
				while (j < strippedLines.size()) {
					String next = strippedLines.get(j).trim();
					if (next.equals("[[rules]]") || findSeparator(strippedLines.get(j)) >= 0) {
						break;
					}
					if (!next.isEmpty()) {
						value.append('\n').append(strippedLines.get(j));
					}
					j++;
				}
				i = j - 1;
			}
			current.setValue(key, value.toString().trim());
		}
		return builders.stream()
			.map(RuleBuilder::build)
			.flatMap(Optional::stream)
			.toList();
	}

	private static String stripComments(String text) {
		StringBuilder result = new StringBuilder();
		boolean inQuotes = false;
		boolean inBlockComment = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (inBlockComment) {
				if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '$') {
					inBlockComment = false;
					i++;
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

	private static final class RuleBuilder {
		private String name = "unnamed";
		private String output;
		private String input;
		private String recipe;
		private boolean hasName;
		private boolean hasOutput;
		private boolean hasInput;
		private boolean hasRecipe;
		private boolean valid = true;

		private void setValue(String key, String value) {
			switch (key) {
				case "name" -> {
					if (hasName) {
						invalidate("duplicate name");
					} else if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
						invalidate("name must not be quoted");
					} else {
						hasName = true;
						name = value;
					}
				}
				case "output" -> {
					if (hasOutput) {
						invalidate("duplicate output");
					} else {
						hasOutput = true;
						output = value;
					}
				}
				case "input" -> {
					if (hasInput) {
						invalidate("duplicate input");
					} else {
						hasInput = true;
						input = value;
					}
				}
				case "recipe" -> {
					if (hasRecipe) {
						invalidate("duplicate recipe");
					} else {
						hasRecipe = true;
						recipe = value;
					}
				}
				default -> invalidate("unknown key '" + key + "' (target and recipe_type are removed; use output)");
			}
		}

		private void invalidate(String reason) {
			if (valid) {
				LOGGER.error("Skipping invalid recipe preference rule {}: {}", name, reason);
			}
			valid = false;
		}

		private Optional<RecipePreferenceRule> build() {
			if (!valid) {
				return Optional.empty();
			}
			if (output == null || output.isBlank()) {
				invalidate("missing output");
				return Optional.empty();
			}
			if (input == null && recipe == null) {
				invalidate("neither input nor recipe");
				return Optional.empty();
			}
			Optional<RecipePreferenceExpression> outputExpression = RecipePreferenceExpression.parseIngredient(output);
			if (outputExpression.isEmpty()) {
				invalidate("invalid output expression");
				return Optional.empty();
			}
			Optional<RecipePreferenceExpression> inputExpression = input == null ?
				Optional.empty() :
				RecipePreferenceExpression.parseIngredient(input);
			if (input != null && inputExpression.isEmpty()) {
				invalidate("invalid input expression");
				return Optional.empty();
			}
			Optional<RecipePreferenceExpression> recipeExpression = recipe == null ?
				Optional.empty() :
				RecipePreferenceExpression.parseUid(recipe);
			if (recipe != null && recipeExpression.isEmpty()) {
				invalidate("invalid recipe expression");
				return Optional.empty();
			}
			return Optional.of(new RecipePreferenceRule(name, outputExpression.get(), inputExpression, recipeExpression));
		}

	}
}

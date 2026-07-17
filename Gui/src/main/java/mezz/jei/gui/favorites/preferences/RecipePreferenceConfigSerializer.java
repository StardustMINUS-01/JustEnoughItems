package mezz.jei.gui.favorites.preferences;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RecipePreferenceConfigSerializer {
	private RecipePreferenceConfigSerializer() {
	}

	public static List<RecipePreferenceRule> deserialize(List<String> lines) {
		List<RuleBuilder> builders = new ArrayList<>();
		RuleBuilder current = null;
		for (int index = 0; index < lines.size(); index++) {
			String line = stripComment(lines.get(index)).trim();
			if (line.isEmpty()) {
				continue;
			}
			if ("[[rules]]".equals(line)) {
				current = new RuleBuilder();
				builders.add(current);
				continue;
			}
			if (current == null) {
				continue;
			}
			int separator = line.indexOf('=');
			if (separator < 0) {
				continue;
			}
			String key = line.substring(0, separator).trim();
			String value = line.substring(separator + 1).trim();
			if (value.startsWith("[")) {
				StringBuilder arrayValue = new StringBuilder(value);
				int balance = getSquareBracketBalance(value);
				while (balance > 0 && ++index < lines.size()) {
					String nextLine = stripComment(lines.get(index)).trim();
					arrayValue.append(nextLine);
					balance += getSquareBracketBalance(nextLine);
				}
				if (balance == 0) {
					current.setMatrix(key, parseStringMatrix(arrayValue.toString()));
				}
			} else {
				current.setValue(key, unquote(value));
			}
		}
		return builders.stream()
			.map(RuleBuilder::build)
			.flatMap(Optional::stream)
			.toList();
	}

	private static int getSquareBracketBalance(String value) {
		boolean quoted = false;
		int balance = 0;
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character == '"') {
				quoted = !quoted;
			} else if (!quoted && character == '[') {
				balance++;
			} else if (!quoted && character == ']') {
				balance--;
			}
		}
		return balance;
	}

	private static Optional<List<List<String>>> parseStringMatrix(String value) {
		try {
			return Optional.of(new MatrixParser(value).parse());
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static String stripComment(String line) {
		boolean quoted = false;
		for (int index = 0; index < line.length(); index++) {
			char character = line.charAt(index);
			if (character == '"') {
				quoted = !quoted;
			} else if (character == '#' && !quoted) {
				return line.substring(0, index);
			}
		}
		return line;
	}

	private static String unquote(String value) {
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

	private static final class RuleBuilder {
		private String name = "unnamed";
		private String target;
		private String recipeType;
		private List<List<String>> input = List.of();
		private List<List<String>> recipe = List.of();

		private void setValue(String key, String value) {
			switch (key) {
				case "name" -> name = value;
				case "target" -> target = value;
				case "recipe_type" -> recipeType = value;
				default -> {
				}
			}
		}

		private void setMatrix(String key, Optional<List<List<String>>> matrix) {
			if (matrix.isEmpty()) {
				return;
			}
			switch (key) {
				case "input" -> input = matrix.get();
				case "recipe" -> recipe = matrix.get();
				default -> {
				}
			}
		}

		private Optional<RecipePreferenceRule> build() {
			if (input.isEmpty() && recipe.isEmpty()) {
				return Optional.empty();
			}
			Optional<RecipePreferenceTarget> parsedTarget = RecipePreferenceTarget.parse(target);
			if (parsedTarget.isEmpty()) {
				return Optional.empty();
			}
			Optional<ResourceLocation> parsedRecipeType = Optional.empty();
			if (recipeType != null && !recipeType.isBlank()) {
				try {
					parsedRecipeType = Optional.of(ResourceLocation.parse(recipeType));
				} catch (RuntimeException e) {
					return Optional.empty();
				}
			}
			List<List<RecipePreferenceTarget>> inputTiers = input.stream()
				.map(tier -> tier.stream().map(RecipePreferenceTarget::parse).flatMap(Optional::stream).toList())
				.filter(tier -> !tier.isEmpty())
				.toList();
			List<List<String>> recipeTiers = recipe.stream()
				.map(tier -> tier.stream().filter(value -> !value.isBlank()).toList())
				.filter(tier -> !tier.isEmpty())
				.toList();
			if (inputTiers.isEmpty() && recipeTiers.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(new RecipePreferenceRule(name, parsedTarget.get(), parsedRecipeType, inputTiers, recipeTiers));
		}
	}

	private static final class MatrixParser {
		private final String text;
		private int index;

		private MatrixParser(String text) {
			this.text = text;
		}

		private List<List<String>> parse() {
			skipWhitespace();
			expect('[');
			List<List<String>> result = new ArrayList<>();
			while (true) {
				skipWhitespace();
				if (consume(']')) {
					return List.copyOf(result);
				}
				result.add(parseRow());
				skipWhitespace();
				if (!consume(',')) {
					expect(']');
					return List.copyOf(result);
				}
			}
		}

		private List<String> parseRow() {
			skipWhitespace();
			expect('[');
			List<String> result = new ArrayList<>();
			while (true) {
				skipWhitespace();
				if (consume(']')) {
					return List.copyOf(result);
				}
				result.add(parseString());
				skipWhitespace();
				if (!consume(',')) {
					expect(']');
					return List.copyOf(result);
				}
			}
		}

		private String parseString() {
			expect('"');
			StringBuilder result = new StringBuilder();
			while (index < text.length()) {
				char character = text.charAt(index++);
				if (character == '"') {
					return result.toString();
				}
				if (character == '\\' && index < text.length()) {
					result.append(text.charAt(index++));
				} else {
					result.append(character);
				}
			}
			throw new IllegalArgumentException("Unterminated string");
		}

		private void skipWhitespace() {
			while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
				index++;
			}
		}

		private boolean consume(char expected) {
			if (index < text.length() && text.charAt(index) == expected) {
				index++;
				return true;
			}
			return false;
		}

		private void expect(char expected) {
			if (!consume(expected)) {
				throw new IllegalArgumentException("Expected " + expected);
			}
		}
	}
}

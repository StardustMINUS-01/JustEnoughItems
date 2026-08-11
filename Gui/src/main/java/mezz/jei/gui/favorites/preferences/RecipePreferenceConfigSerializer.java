package mezz.jei.gui.favorites.preferences;

import mezz.jei.gui.config.ConfigLineReader;
import mezz.jei.gui.match.IngredientExpression;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses recipe preference rules where each "output =" line starts a new rule.
 * Only output / input / recipe are interpreted; unknown keys and other lines
 * are skipped. "[[section]]" lines act as value boundaries and carry no meaning.
 */
public final class RecipePreferenceConfigSerializer {
	private static final Logger LOGGER = LogManager.getLogger();

	private RecipePreferenceConfigSerializer() {}

	public static List<RecipePreferenceRule> deserialize(List<String> lines) {
		List<RecipePreferenceRule> rules = new ArrayList<>();
		RuleBuilder current = null;
		for (ConfigLineReader.Entry entry : ConfigLineReader.read(lines)) {
			switch (entry.key()) {
				case "output" -> {
					if (current != null) {
						current.build().ifPresent(rules::add);
					}
					current = new RuleBuilder(entry.value());
				}
				case "input" -> {
					if (current == null) {
						LOGGER.error("Skipping input without preceding output: {}", entry.value());
					} else {
						current.setInput(entry.value());
					}
				}
				case "recipe" -> {
					if (current == null) {
						LOGGER.error("Skipping recipe without preceding output: {}", entry.value());
					} else {
						current.setRecipe(entry.value());
					}
				}
				default -> {
					// Unknown key (including "name"): skip as an unknown line.
				}
			}
		}
		if (current != null) {
			current.build().ifPresent(rules::add);
		}
		return rules;
	}

	private static final class RuleBuilder {
		private final String output;
		private String input;
		private String recipe;
		private boolean valid = true;

		private RuleBuilder(String output) {
			this.output = output;
		}

		private void setInput(String value) {
			if (input != null) {
				invalidate("duplicate input");
			} else {
				input = value;
			}
		}

		private void setRecipe(String value) {
			if (recipe != null) {
				invalidate("duplicate recipe");
			} else {
				recipe = value;
			}
		}

		private void invalidate(String reason) {
			if (valid) {
				LOGGER.error("Skipping invalid recipe preference rule (output {}): {}", output, reason);
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
			Optional<IngredientExpression> outputExpression = IngredientExpression.parseIngredient(output);
			if (outputExpression.isEmpty()) {
				invalidate("invalid output expression");
				return Optional.empty();
			}
			Optional<IngredientExpression> inputExpression = input == null ?
				Optional.empty() :
				IngredientExpression.parseIngredient(input);
			if (input != null && inputExpression.isEmpty()) {
				invalidate("invalid input expression");
				return Optional.empty();
			}
			Optional<IngredientExpression> recipeExpression = recipe == null ?
				Optional.empty() :
				IngredientExpression.parseUid(recipe);
			if (recipe != null && recipeExpression.isEmpty()) {
				invalidate("invalid recipe expression");
				return Optional.empty();
			}
			return Optional.of(new RecipePreferenceRule(outputExpression.get(), inputExpression, recipeExpression));
		}
	}
}

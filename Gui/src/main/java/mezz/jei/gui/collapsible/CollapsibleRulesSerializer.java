package mezz.jei.gui.collapsible;

import mezz.jei.gui.config.ConfigLineReader;
import mezz.jei.gui.match.IngredientExpression;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses collapsible items rules where each "item =" line is one fold group.
 * Unknown keys and other lines are skipped; "[[section]]" lines act as value
 * boundaries and carry no meaning.
 */
public final class CollapsibleRulesSerializer {
	private static final Logger LOGGER = LogManager.getLogger();

	private CollapsibleRulesSerializer() {}

	public static CollapsibleRules deserialize(List<String> lines) {
		return deserialize(lines, "collapsible rules");
	}

	public static CollapsibleRules deserialize(List<String> lines, String source) {
		List<CollapsibleGroup> groups = new ArrayList<>();
		for (ConfigLineReader.Entry entry : ConfigLineReader.read(lines)) {
			if (!"item".equals(entry.key())) {
				continue;
			}
			String value = entry.value();
			Optional<IngredientExpression> expression = IngredientExpression.parseIngredient(value);
			if (value.isBlank() || expression.isEmpty()) {
				LOGGER.error("Skipping invalid collapsible group in {}: {}", source, value);
				continue;
			}
			groups.add(CollapsibleGroup.create(value, expression.get()));
		}
		return new CollapsibleRules(groups);
	}

}

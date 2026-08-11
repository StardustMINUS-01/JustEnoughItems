package mezz.jei.gui.collapsible;

import mezz.jei.gui.match.IngredientExpression;
import mezz.jei.gui.match.IngredientMatchInfo;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * One fold group defined by a single "item =" rule.
 * The stable id is derived from the expression text.
 */
public final class CollapsibleGroup {
	private final String id;
	private final String expressionText;
	private final IngredientExpression matcher;

	private CollapsibleGroup(String expressionText, IngredientExpression matcher) {
		this.expressionText = expressionText;
		this.matcher = matcher;
		this.id = UUID.nameUUIDFromBytes(expressionText.getBytes(StandardCharsets.UTF_8)).toString();
	}

	public static CollapsibleGroup create(String expressionText, IngredientExpression matcher) {
		return new CollapsibleGroup(expressionText, matcher);
	}

	public String id() {
		return id;
	}

	public String expressionText() {
		return expressionText;
	}

	public boolean matches(IngredientMatchInfo info) {
		return matcher.matches(info);
	}
}

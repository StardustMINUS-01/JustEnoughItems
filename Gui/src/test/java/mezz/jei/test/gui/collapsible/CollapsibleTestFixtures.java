package mezz.jei.test.gui.collapsible;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.common.ingredients.TypedIngredient;
import mezz.jei.gui.collapsible.CollapsibleGroup;
import mezz.jei.gui.match.IngredientExpression;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;

final class CollapsibleTestFixtures {
	private static final IIngredientType<Object> TYPE = new IIngredientType<>() {
		@Override
		public Class<? extends Object> getIngredientClass() {
			return Object.class;
		}

		@Override
		public String getUid() {
			return "test";
		}
	};

	private CollapsibleTestFixtures() {
	}

	static CollapsibleGroup group(String expression) {
		return CollapsibleGroup.create(expression, IngredientExpression.parseIngredient(expression).orElseThrow());
	}

	static IElement<?> element(String itemId) {
		return new IngredientElement<>(TypedIngredient.createUnvalidated(TYPE, itemId));
	}
}

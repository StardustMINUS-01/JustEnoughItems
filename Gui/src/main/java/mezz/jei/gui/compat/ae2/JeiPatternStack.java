package mezz.jei.gui.compat.ae2;

import mezz.jei.api.ingredients.ITypedIngredient;

public record JeiPatternStack(
	Kind kind,
	ITypedIngredient<?> ingredient,
	long amount
) {
	public JeiPatternStack {
		if (amount <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}
	}

	public enum Kind {
		ITEM,
		FLUID
	}
}

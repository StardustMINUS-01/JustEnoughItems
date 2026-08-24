package mezz.jei.gui.compat.ae2;

import java.util.Objects;

public record JeiPatternCatalyst(int sourceSlot, JeiPatternStack stack) {
	public JeiPatternCatalyst {
		if (sourceSlot < 0) {
			throw new IllegalArgumentException("Catalyst source slot must not be negative");
		}
		Objects.requireNonNull(stack, "stack");
	}
}

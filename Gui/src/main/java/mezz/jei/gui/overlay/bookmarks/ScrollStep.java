package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.SaturatedMath;

public class ScrollStep {
	private long value;
	private final java.util.function.BooleanSupplier enabled;

	public ScrollStep() {
		this(() -> true);
	}

	public ScrollStep(java.util.function.BooleanSupplier enabled) {
		this.enabled = enabled;
	}

	public long getValue() {
		return value;
	}

	public void setValue(long value) {
		this.value = Math.max(0, Math.min(Integer.MAX_VALUE, value));
	}

	public void add(long delta) {
		setValue(SaturatedMath.add(value, delta));
	}

	public long getEffectiveStep() {
		return !enabled.getAsBoolean() || value == 0 ? 64 : value;
	}

	public void reset() {
		value = 0;
	}
}

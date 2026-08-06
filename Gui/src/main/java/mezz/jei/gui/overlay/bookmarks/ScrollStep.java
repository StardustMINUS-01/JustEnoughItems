package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.SaturatedMath;

public class ScrollStep {
	private long value;

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
		return value == 0 ? 64 : value;
	}

	public void reset() {
		value = 0;
	}
}

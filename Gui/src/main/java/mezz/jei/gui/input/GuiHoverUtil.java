package mezz.jei.gui.input;

import mezz.jei.common.util.ImmutablePoint2i;

public final class GuiHoverUtil {
	private static final ImmutablePoint2i OUTSIDE = new ImmutablePoint2i(-10000, -10000);

	private GuiHoverUtil() {}

	public static ImmutablePoint2i backgroundMouse(boolean blocked, int mouseX, int mouseY) {
		return blocked ? OUTSIDE : new ImmutablePoint2i(mouseX, mouseY);
	}
}

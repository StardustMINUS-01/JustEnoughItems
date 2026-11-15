package mezz.jei.api.gui.tooltip;

import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.Optional;

/** Client-thread, session-scoped context. Coordinates are scaled GUI units, not framebuffer pixels. */
@org.jetbrains.annotations.ApiStatus.NonExtendable
public interface ITooltipInteractionContext {
	ScreenRectangle getBounds();
	ScreenRectangle getVisibleBounds();
	double getMouseX();
	double getMouseY();
	boolean isPinned();
	boolean isFocused();
	boolean isMouseCaptured();
	boolean isActive();
	Optional<ITypedIngredient<?>> getIngredient();
	void requestFocus();
	void releaseFocus();
	void releaseMouseCapture();
	/** Re-measure before the next render; stale hit regions are disabled immediately. */
	void requestLayout();
	void close();
}

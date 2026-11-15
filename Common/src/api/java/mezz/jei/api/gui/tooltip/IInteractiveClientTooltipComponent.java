package mezz.jei.api.gui.tooltip;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * Optional JEIU client tooltip interaction. Keep registering TooltipComponent factories normally.
 * All callbacks run on the client thread. Every pinned mount has one matching onClosed callback.
 * Hover-only rendering calls onLayout with an inactive, unpinned context and does not capture input.
 */
public interface IInteractiveClientTooltipComponent extends ClientTooltipComponent {
	default void onMounted(ITooltipInteractionContext context) {}
	default void onPinnedChanged(ITooltipInteractionContext context) {}
	default void onLayout(ITooltipInteractionContext context) {}
	default void onTick(ITooltipInteractionContext context) {}
	default void onFocusChanged(ITooltipInteractionContext context) {}
	default void onInteractionCancelled(ITooltipInteractionContext context) {}
	default void onClosed(ITooltipInteractionContext context, TooltipCloseReason reason) {}
	default boolean isFocusable() { return false; }
	default TooltipPointerResult mousePressed(ITooltipInteractionContext context, int button, int modifiers) { return TooltipPointerResult.PASS; }
	default boolean mouseReleased(ITooltipInteractionContext context, int button, int modifiers) { return false; }
	default boolean mouseDragged(ITooltipInteractionContext context, int button, double deltaX, double deltaY) { return false; }
	default boolean mouseScrolled(ITooltipInteractionContext context, double deltaX, double deltaY) { return false; }
	default boolean keyPressed(ITooltipInteractionContext context, int keyCode, int scanCode, int modifiers) { return false; }
	default boolean keyReleased(ITooltipInteractionContext context, int keyCode, int scanCode, int modifiers) { return false; }
	default boolean charTyped(ITooltipInteractionContext context, char character, int modifiers) { return false; }
}

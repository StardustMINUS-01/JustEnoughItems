package mezz.jei.forge.chat;

import mezz.jei.gui.chat.ChatRecipeTooltip;
import mezz.jei.gui.input.PinnedTooltipManager;
import mezz.jei.forge.events.PermanentEventSubscriptions;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

public final class JeiChatTooltipEventHandler {
	private static boolean renderingJeiChatTooltip;
	private static final ChatRecipeTooltip recipeTooltip = ChatRecipeTooltip.INSTANCE;

	private JeiChatTooltipEventHandler() {
	}

	public static void register(PermanentEventSubscriptions subscriptions) {
		subscriptions.register(RenderTooltipEvent.Pre.class, JeiChatTooltipEventHandler::onRenderTooltipPre);
		subscriptions.register(ScreenEvent.Render.Pre.class, event -> recipeTooltip.update(event.getScreen(), event.getMouseX(), event.getMouseY()));
		subscriptions.register(ScreenEvent.Render.Post.class, event -> {
			renderingJeiChatTooltip = true;
			try {
				recipeTooltip.draw(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
			} finally {
				renderingJeiChatTooltip = false;
			}
		});
		subscriptions.register(ScreenEvent.Opening.class, event -> recipeTooltip.clear());
		subscriptions.register(ClientPlayerNetworkEvent.LoggingOut.class, event -> recipeTooltip.onRuntimeStopped());
		subscriptions.register(TickEvent.ClientTickEvent.class, event -> {
			if (event.phase == TickEvent.Phase.END) {
				recipeTooltip.tick();
			}
		});
	}

	private static void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
		if (renderingJeiChatTooltip) {
			return;
		}
		if (recipeTooltip.hasTags()) {
			if (PinnedTooltipManager.shouldSuppressExternalTooltip()) {
				event.setCanceled(true);
			}
		} else if (recipeTooltip.isVisible()) {
			event.setCanceled(true);
		}
	}
}

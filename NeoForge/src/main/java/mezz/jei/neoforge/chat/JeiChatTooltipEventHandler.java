package mezz.jei.neoforge.chat;

import mezz.jei.gui.chat.ChatIngredientTooltip;
import mezz.jei.gui.chat.ChatIngredientTooltip.IngredientTooltipData;
import mezz.jei.gui.chat.ChatRecipeTooltip;
import mezz.jei.neoforge.events.PermanentEventSubscriptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.Optional;

public final class JeiChatTooltipEventHandler {
	private static boolean renderingJeiChatTooltip;
	private static final ChatRecipeTooltip recipeTooltip = new ChatRecipeTooltip();

	private JeiChatTooltipEventHandler() {
	}

	public static void register(PermanentEventSubscriptions subscriptions) {
		subscriptions.register(RenderTooltipEvent.Pre.class, JeiChatTooltipEventHandler::onRenderTooltipPre);
		subscriptions.register(ScreenEvent.Render.Pre.class, event -> recipeTooltip.update(event.getScreen(), event.getMouseX(), event.getMouseY()));
		subscriptions.register(ScreenEvent.Opening.class, event -> recipeTooltip.clear());
		subscriptions.register(ClientPlayerNetworkEvent.LoggingOut.class, event -> recipeTooltip.clear());
		subscriptions.register(ClientTickEvent.Post.class, event -> recipeTooltip.tick());
	}

	private static void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
		if (renderingJeiChatTooltip) {
			return;
		}
		renderingJeiChatTooltip = true;
		try {
			if (recipeTooltip.draw(event.getGraphics(), event.getX(), event.getY())) {
				event.setCanceled(true);
				return;
			}
		} finally {
			renderingJeiChatTooltip = false;
		}

		Minecraft minecraft = Minecraft.getInstance();
		Screen screen = minecraft.screen;
		Optional<IngredientTooltipData<?>> optionalTooltipData = ChatIngredientTooltip.getTooltipForHoveredChatLink(
			screen,
			event.getX(),
			event.getY()
		);
		if (optionalTooltipData.isEmpty()) {
			return;
		}

		IngredientTooltipData<?> tooltipData = optionalTooltipData.get();
		if (renderJeiChatTooltip(event, tooltipData)) {
			event.setCanceled(true);
		}
	}

	private static <T> boolean renderJeiChatTooltip(RenderTooltipEvent.Pre event, IngredientTooltipData<T> tooltipData) {
		if (tooltipData.tooltip().isEmpty()) {
			return false;
		}

		renderingJeiChatTooltip = true;
		try {
			tooltipData.draw(event.getGraphics(), event.getX(), event.getY());
		} finally {
			renderingJeiChatTooltip = false;
		}
		return true;
	}
}

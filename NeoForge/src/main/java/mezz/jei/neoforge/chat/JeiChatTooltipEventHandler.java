package mezz.jei.neoforge.chat;

import mezz.jei.gui.chat.ChatIngredientTooltip.IngredientTooltipData;
import mezz.jei.gui.chat.ChatRecipeTooltip;
import mezz.jei.gui.input.PinnedTooltipManager;
import mezz.jei.neoforge.events.PermanentEventSubscriptions;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.Optional;

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
				PinnedTooltipManager.draw(recipeTooltip, () -> recipeTooltip.draw(event.getGuiGraphics(), event.getMouseX(), event.getMouseY()));
			} finally {
				renderingJeiChatTooltip = false;
			}
		});
		subscriptions.register(ScreenEvent.Opening.class, event -> recipeTooltip.clear());
		subscriptions.register(ClientPlayerNetworkEvent.LoggingOut.class, event -> recipeTooltip.clear());
		subscriptions.register(ClientTickEvent.Post.class, event -> recipeTooltip.tick());
	}

	private static void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
		if (renderingJeiChatTooltip) {
			return;
		}
		if (recipeTooltip.hasTags()) {
			if (PinnedTooltipManager.shouldSuppressExternalTooltip()) {
				event.setCanceled(true);
			}
			return;
		}
		if (!recipeTooltip.isUnpinnedIngredient()) {
			if (recipeTooltip.isVisible()) {
				event.setCanceled(true);
			}
			return;
		}

		Optional<IngredientTooltipData<?>> optionalTooltipData = recipeTooltip.getIngredientTooltip();
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
			recipeTooltip.drawIngredientTooltip(event.getGraphics(), tooltipData);
		} finally {
			renderingJeiChatTooltip = false;
		}
		return true;
	}
}

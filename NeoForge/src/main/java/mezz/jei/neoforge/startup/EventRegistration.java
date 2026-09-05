package mezz.jei.neoforge.startup;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.common.Internal;
import mezz.jei.neoforge.events.RuntimeEventSubscriptions;
import mezz.jei.neoforge.input.ForgeUserInput;
import mezz.jei.gui.events.GuiEventHandler;
import mezz.jei.gui.input.ClientInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.WorldInputHandler;
import mezz.jei.gui.startup.JeiEventHandlers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

public class EventRegistration {
	public static void registerEvents(RuntimeEventSubscriptions subscriptions, JeiEventHandlers eventHandlers) {
		ClientInputHandler clientInputHandler = eventHandlers.clientInputHandler();
		registerClientInputHandler(subscriptions, clientInputHandler);
		registerWorldInputHandler(subscriptions, eventHandlers.worldInputHandler());

		GuiEventHandler guiEventHandler = eventHandlers.guiEventHandler();
		registerGuiHandler(subscriptions, guiEventHandler);
	}

	private static void registerWorldInputHandler(RuntimeEventSubscriptions subscriptions, WorldInputHandler handler) {
		subscriptions.register(InputEvent.Key.class, event -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (event.getAction() == InputConstants.PRESS && minecraft.screen == null) {
				handler.handleUserInput(
					ForgeUserInput.fromEvent(event),
					Internal.getKeyMappings(),
					() -> getPickedStack(minecraft)
				);
			}
		});
	}

	private static ItemStack getPickedStack(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null || minecraft.hitResult == null) {
			return ItemStack.EMPTY;
		}
		if (minecraft.hitResult instanceof BlockHitResult hitResult) {
			var pos = hitResult.getBlockPos();
			var state = minecraft.level.getBlockState(pos);
			return state.getBlock().getCloneItemStack(state, hitResult, minecraft.level, pos, minecraft.player);
		}
		if (minecraft.hitResult instanceof EntityHitResult hitResult) {
			if (hitResult.getEntity() instanceof ItemEntity itemEntity) {
				return itemEntity.getItem();
			}
			ItemStack pickedStack = hitResult.getEntity().getPickedResult(hitResult);
			// NeoForge permits null when an entity has no pick-block representation.
			return pickedStack == null ? ItemStack.EMPTY : pickedStack;
		}
		return ItemStack.EMPTY;
	}

	private static void registerClientInputHandler(RuntimeEventSubscriptions subscriptions, ClientInputHandler handler) {
		subscriptions.register(ScreenEvent.Init.Post.class, event -> handler.onInitGui());

		subscriptions.register(ScreenEvent.KeyPressed.Pre.class, event -> {
			Screen screen = event.getScreen();
			UserInput input = ForgeUserInput.fromEvent(event);
			if (handler.onKeyboardKeyPressedPre(screen, input)) {
				event.setCanceled(true);
			}
		});
		subscriptions.register(ScreenEvent.KeyPressed.Post.class, event -> {
			Screen screen = event.getScreen();
			UserInput input = ForgeUserInput.fromEvent(event);
			if (handler.onKeyboardKeyPressedPost(screen, input)) {
				event.setCanceled(true);
			}
		});

		subscriptions.register(ScreenEvent.KeyReleased.Pre.class, event -> {
			UserInput input = ForgeUserInput.fromEvent(event);
			handler.onKeyboardKeyReleased(input);
		});

		subscriptions.register(ScreenEvent.CharacterTyped.Pre.class, event -> {
			Screen screen = event.getScreen();
			char codePoint = event.getCodePoint();
			int modifiers = event.getModifiers();
			if (handler.onKeyboardCharTypedPre(screen, codePoint, modifiers)) {
				event.setCanceled(true);
			}
		});
		subscriptions.register(ScreenEvent.CharacterTyped.Post.class, event -> {
			Screen screen = event.getScreen();
			char codePoint = event.getCodePoint();
			int modifiers = event.getModifiers();
			handler.onKeyboardCharTypedPost(screen, codePoint, modifiers);
		});

		subscriptions.register(ScreenEvent.MouseButtonPressed.Pre.class, event ->
			ForgeUserInput.fromEvent(event)
				.ifPresent(input -> {
					Screen screen = event.getScreen();
					if (handler.onGuiMouseClicked(screen, input)) {
						event.setCanceled(true);
					}
				})
		);
		subscriptions.register(ScreenEvent.MouseButtonReleased.Pre.class, event ->
			ForgeUserInput.fromEvent(event)
				.ifPresent(input -> {
					Screen screen = event.getScreen();
					if (handler.onGuiMouseReleased(screen, input)) {
						event.setCanceled(true);
					}
				})
		);

		subscriptions.register(ScreenEvent.MouseScrolled.Pre.class, event -> {
			double mouseX = event.getMouseX();
			double mouseY = event.getMouseY();
			double scrollDeltaX = event.getScrollDeltaX();
			double scrollDeltaY = event.getScrollDeltaY();
			if (handler.onGuiMouseScroll(mouseX, mouseY, scrollDeltaX, scrollDeltaY)) {
				event.setCanceled(true);
			}
		});

		subscriptions.register(ScreenEvent.MouseDragged.Pre.class, event -> {
			Screen screen = event.getScreen();
			if (handler.onGuiMouseDragged(screen, event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY())) {
				event.setCanceled(true);
			}
		});
	}

	@SuppressWarnings("removal")
	public static void registerGuiHandler(
		RuntimeEventSubscriptions subscriptions,
		GuiEventHandler guiEventHandler
	) {
		subscriptions.register(ClientTickEvent.Post.class, event -> {
			if (Minecraft.getInstance().screen != null) {
				guiEventHandler.onClientTick();
			}
		});
		subscriptions.register(ScreenEvent.Init.Post.class, event -> {
			Screen screen = event.getScreen();
			guiEventHandler.onGuiInit(screen);
		});
		subscriptions.register(ScreenEvent.Opening.class, event -> {
			Screen screen = event.getScreen();
			guiEventHandler.onGuiOpen(screen);
		});
		subscriptions.register(EventPriority.LOWEST, ContainerScreenEvent.Render.Foreground.class, event -> {
			AbstractContainerScreen<?> containerScreen = event.getContainerScreen();
			var guiGraphics = event.getGuiGraphics();
			int mouseX = event.getMouseX();
			int mouseY = event.getMouseY();
			runWithIdentityPose(guiGraphics, () ->
				guiEventHandler.drawForContainerScreen(containerScreen, guiGraphics, mouseX, mouseY)
			);
		});
		subscriptions.register(EventPriority.HIGHEST, ContainerScreenEvent.Render.Background.class, event -> {
			AbstractContainerScreen<?> containerScreen = event.getContainerScreen();
			var guiGraphics = event.getGuiGraphics();
			int mouseX = event.getMouseX();
			int mouseY = event.getMouseY();
			runWithIdentityPose(guiGraphics, () ->
				guiEventHandler.drawForScreen(containerScreen, guiGraphics, mouseX, mouseY)
			);
		});
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.Render.Post.class, event -> {
			Screen screen = event.getScreen();
			if (screen instanceof AbstractContainerScreen<?>) {
				return;
			}
			var guiGraphics = event.getGuiGraphics();
			int mouseX = event.getMouseX();
			int mouseY = event.getMouseY();
			runWithIdentityPose(guiGraphics, () ->
				guiEventHandler.drawForScreen(screen, guiGraphics, mouseX, mouseY)
			);
		});
		subscriptions.register(ScreenEvent.RenderInventoryMobEffects.class, event -> {
			if (guiEventHandler.renderCompactPotionIndicators()) {
				// Forcibly renders the potion indicators in compact mode.
				// This gives the ingredient list overlay more room to display ingredients.
				event.setCompact(true);
			}
		});
	}

	private static void runWithIdentityPose(GuiGraphics graphics, Runnable runnable) {
		PoseStack pose = graphics.pose();
		float z = pose.last().pose().m32();
		pose.pushPose();
		pose.setIdentity();
		pose.translate(0, 0, z);
		try {
			runnable.run();
		} finally {
			pose.popPose();
		}
	}
}

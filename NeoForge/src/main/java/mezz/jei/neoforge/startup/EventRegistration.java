package mezz.jei.neoforge.startup;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.common.Internal;
import mezz.jei.neoforge.events.RuntimeEventSubscriptions;
import mezz.jei.neoforge.input.ForgeUserInput;
import mezz.jei.gui.events.GuiEventHandler;
import mezz.jei.gui.input.ClientInputHandler;
import mezz.jei.gui.input.PinnedTooltipManager;
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
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

public class EventRegistration {
	public static void registerEvents(RuntimeEventSubscriptions subscriptions, JeiEventHandlers eventHandlers) {
		GuiEventHandler taskHandler = eventHandlers.guiEventHandler();
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.MouseButtonPressed.Pre.class, event -> taskHandler.stopAutoCrafting());
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.KeyPressed.Pre.class, event -> taskHandler.stopAutoCrafting());
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.MouseScrolled.Pre.class, event -> taskHandler.stopAutoCrafting());
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.MouseDragged.Pre.class, event -> taskHandler.stopAutoCrafting());
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

	public static void registerInteractiveTooltips(mezz.jei.neoforge.events.PermanentEventSubscriptions subscriptions) {
		var host = mezz.jei.gui.input.InteractiveTooltipController.INSTANCE;
		subscriptions.register(ClientTickEvent.Post.class, event -> host.tick());
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.MouseButtonPressed.Pre.class, event -> {
			if (host.mousePressed(event.getMouseX(), event.getMouseY(), event.getButton(), modifiers()))
				event.setCanceled(true);
		});
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.MouseButtonReleased.Pre.class, event -> {
			if (host.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton(), modifiers()))
				event.setCanceled(true);
		});
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.MouseDragged.Pre.class, event -> {
			if (host.mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY()))
				event.setCanceled(true);
		});
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.MouseScrolled.Pre.class, event -> {
			if (host.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaX(), event.getScrollDeltaY()))
				event.setCanceled(true);
		});
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.KeyPressed.Pre.class, event -> {
			if (host.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers()))
				event.setCanceled(true);
		});
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.KeyReleased.Pre.class, event -> {
			if (host.keyReleased(event.getKeyCode(), event.getScanCode(), event.getModifiers()))
				event.setCanceled(true);
		});
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.CharacterTyped.Pre.class, event -> {
			if (host.charTyped(event.getCodePoint(), event.getModifiers()))
				event.setCanceled(true);
		});
		subscriptions.register(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut.class, event -> host.stop());
	}

	private static int modifiers() {
		return (Screen.hasShiftDown() ? org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT : 0) |
			(Screen.hasControlDown() ? org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL : 0) |
			(Screen.hasAltDown() ? org.lwjgl.glfw.GLFW.GLFW_MOD_ALT : 0);
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

		subscriptions.register(ScreenEvent.MouseButtonPressed.Pre.class, event -> ForgeUserInput.fromEvent(event)
			.ifPresent(input -> {
				Screen screen = event.getScreen();
				if (handler.onGuiMouseClicked(screen, input)) {
					event.setCanceled(true);
				}
			})
		);
		subscriptions.register(ScreenEvent.MouseButtonReleased.Pre.class, event -> ForgeUserInput.fromEvent(event)
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
			if (handler.onGuiMouseScroll(event.getScreen(), mouseX, mouseY, scrollDeltaX, scrollDeltaY)) {
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
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.Render.Pre.class, event -> {
			Screen screen = event.getScreen();
			guiEventHandler.updateForScreenRender(screen, event.getMouseX(), event.getMouseY());
		});
		subscriptions.register(EventPriority.HIGHEST, RenderTooltipEvent.Pre.class, event -> {
			if (PinnedTooltipManager.shouldSuppressExternalTooltip()) {
				event.setCanceled(true);
			}
		});
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
			runWithIdentityPose(guiGraphics, () -> guiEventHandler.drawForScreenForeground(containerScreen, guiGraphics, mouseX, mouseY));
		});
		subscriptions.register(EventPriority.HIGHEST, ContainerScreenEvent.Render.Background.class, event -> {
			AbstractContainerScreen<?> containerScreen = event.getContainerScreen();
			var guiGraphics = event.getGuiGraphics();
			runWithIdentityPose(guiGraphics, () -> guiEventHandler.drawForScreenBackground(containerScreen, guiGraphics));
		});
		subscriptions.register(EventPriority.HIGHEST, ScreenEvent.Render.Post.class, event -> {
			Screen screen = event.getScreen();
			if (screen instanceof AbstractContainerScreen<?>) {
				return;
			}
			var guiGraphics = event.getGuiGraphics();
			int mouseX = event.getMouseX();
			int mouseY = event.getMouseY();
			runWithIdentityPose(guiGraphics, () -> {
				guiEventHandler.drawForScreenBackground(screen, guiGraphics);
				guiEventHandler.drawForScreenForeground(screen, guiGraphics, mouseX, mouseY);
			});
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

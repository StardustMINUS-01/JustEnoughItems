package mezz.jei.gui.input;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.tooltip.IInteractiveClientTooltipComponent;
import mezz.jei.api.gui.tooltip.TooltipCloseReason;
import mezz.jei.common.Internal;
import mezz.jei.common.input.IGuiInputLayer;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.MouseUtil;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.interaction.IInputInteraction;
import java.util.Optional;
import mezz.jei.common.gui.GuiRenderLayers;
import mezz.jei.common.platform.Services;
import mezz.jei.common.gui.TooltipRenderScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Single client tooltip host, with interactions captured by the GUI input controller. */
public final class InteractiveTooltipController implements IPinnedTooltipHolder, IGuiInputLayer, ITextInputHandler {
	public static final InteractiveTooltipController INSTANCE = new InteractiveTooltipController();
	private @Nullable TooltipInteractionSession session;
	private @Nullable Screen screen;
	private @Nullable IPinnedTooltipHolder attachedOwner;
	private @Nullable Font font;
	private @Nullable ClientTooltipPositioner attachedPositioner;
	private @Nullable ItemStack stack;
	private boolean rendering;
	private boolean enabled;
	private boolean pinBlocked;
	private java.util.function.BooleanSupplier sourceValid = () -> true;
	private int anchorX;
	private int anchorY;
	private int left;
	private int top;
	private int width;
	private int height;
	private final Set<Integer> pendingReleases = new HashSet<>();
	private final Set<Integer> routedButtons = new HashSet<>();
	private final Set<Integer> pendingKeyReleases = new HashSet<>();

	private InteractiveTooltipController() {}

	public void install() { close(TooltipCloseReason.RUNTIME_STOPPED); enabled = true; }
	public void stop() { close(TooltipCloseReason.RUNTIME_STOPPED); enabled = false; }
	public boolean hasKeyboardFocus() { return session != null && (session.hasFocus() || session.hasCapture()); }
	public boolean isRendering() { return rendering; }

	@Override
	public boolean hasKeyboardPriority() {
		return session != null && session.isActive();
	}

	public InputArea createInputArea() {
		return InputArea.builder("Interactive tooltip", this).controls(this).build();
	}

	@Override
	public void update(double mouseX, double mouseY) {
		update();
	}

	@Override
	public boolean containsMouse(double mouseX, double mouseY) {
		return session != null && (session.hasCapture() || session.isMouseOver(mouseX, mouseY) || inside(mouseX, mouseY));
	}

	@Override
	public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
		draw(graphics);
	}

	@Override
	public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keys) {
		update();
		TooltipInteractionSession current = session;
		if (current == null) {
			return Optional.empty();
		}
		if (input.isKeyboardInput()) {
			return input.ifKeyboardEvent(this::keyPressed) ? Optional.of(new IInputInteraction() {
				@Override
				public void complete(UserInput ignored) {
				}

				@Override
				public boolean consumesCharacters() {
					return !current.hasFocus();
				}
			}) : Optional.empty();
		}
		int button = input.getKey().getValue();
		if (!mousePressed(input.getMouseX(), input.getMouseY(), button, getMouseModifiers(input))) {
			return Optional.empty();
		}
		routedButtons.add(button);
		return Optional.of(new IInputInteraction() {
			@Override
			public void complete(UserInput release) {
				routedButtons.remove(button);
				pendingReleases.remove(button);
				update();
				if (session == current) {
					current.mouseReleased(release.getMouseX(), release.getMouseY(), button, getMouseModifiers(release));
				}
			}

			@Override
			public void drag(double x, double y, InputConstants.Key key, double dx, double dy) {
				update();
				if (session == current) {
					current.mouseDragged(x, y, button, dx, dy);
				}
			}

			@Override
			public boolean capturesMouseMovement() {
				return true;
			}

			@Override
			public boolean keepsCaptureOnRepeatedPress() {
				return true;
			}

			@Override
			public void cancel() {
				routedButtons.remove(button);
				current.cancelInteraction(button);
			}
		});
	}

	private static int getMouseModifiers(UserInput input) {
		return input.getModifiers() |
			(Screen.hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0) |
			(Screen.hasControlDown() ? GLFW.GLFW_MOD_CONTROL : 0) |
			(Screen.hasAltDown() ? GLFW.GLFW_MOD_ALT : 0);
	}

	@Override
	public boolean scroll(double mouseX, double mouseY, double scrollX, double scrollY) {
		return mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void resetInput() {
		close(TooltipCloseReason.SCREEN_CHANGED);
	}

	@Override
	public void focus() {
	}

	@Override
	public void clearFocus() {
		if (session != null) {
			session.clearFocus();
		}
	}

	@Override
	public boolean onCharTyped(char codePoint, int modifiers) {
		return charTyped(codePoint, modifiers);
	}

	public boolean intercept(GuiGraphics graphics, Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, ItemStack tooltipStack) {
		if (!enabled || rendering || TooltipRenderScope.current() == null)
			return false;
		update();
		if (session != null) {
			if (attachedOwner != null && PinnedTooltipManager.getActiveHolder() == attachedOwner) {
				// Candidate grids can draw a separate nested ingredient tooltip in the same owner scope.
				if (positioner != attachedPositioner)
					return false;
				render(graphics, positioner, x, y);
			}
			return true;
		}
		if (components.stream().noneMatch(IInteractiveClientTooltipComponent.class::isInstance))
			return false;
		boolean pinned = !pinBlocked && Internal.getKeyMappings().getPauseRecipeCycling().isDown();
		TooltipInteractionSession created = new TooltipInteractionSession(components, TooltipRenderScope.current().ingredient(), pinned);
		if (!pinned) {
			created.beginFrame(graphics.guiWidth(), graphics.guiHeight(), MouseUtil.getX(), MouseUtil.getY());
			rendering = true;
			try {
				var platform = Services.PLATFORM.getRenderHelper();
				created.setClip(platform.getTooltipClip(graphics));
				platform.renderPreparedTooltip(graphics, font, created.components(), x, y, positioner, tooltipStack);
			} finally {
				rendering = false;
			}
			return true;
		}
		this.session = created;
		this.sourceValid = TooltipRenderScope.current().sourceValid();
		this.screen = Minecraft.getInstance().screen;
		this.font = font;
		this.anchorX = x;
		this.anchorY = y;
		this.stack = tooltipStack.copy();
		this.attachedOwner = PinnedTooltipManager.getActiveHolder();
		this.attachedPositioner = attachedOwner == null ? null : positioner;
		if (attachedOwner == null)
			PinnedTooltipManager.opened(this);
		created.mount();
		if (created.isActive()) {
			IPinnedTooltipHolder owner = attachedOwner == null ? this : attachedOwner;
			PinnedTooltipManager.draw(owner, () -> render(graphics, attachedOwner == null ? this::position : positioner, x, y));
		}
		return true;
	}

	private Vector2i position(int screenWidth, int screenHeight, int x, int y, int tooltipWidth, int tooltipHeight) {
		left = Math.max(4, Math.min(anchorX + 12, screenWidth - tooltipWidth - 4));
		top = Math.max(4, Math.min(anchorY - 12, screenHeight - tooltipHeight - 4));
		width = tooltipWidth;
		height = tooltipHeight;
		return new Vector2i(left, top);
	}

	public void draw(GuiGraphics graphics) {
		update();
		if (session == null || attachedOwner != null)
			return;
		graphics.pose().pushPose();
		try {
			graphics.pose().translate(0, 0, GuiRenderLayers.TOOLTIP_Z);
			PinnedTooltipManager.draw(this, () -> render(graphics, this::position, anchorX, anchorY));
		} finally {
			graphics.pose().popPose();
		}
	}

	private void render(GuiGraphics graphics, ClientTooltipPositioner positioner, int x, int y) {
		TooltipInteractionSession current = session;
		if (current == null || font == null || stack == null)
			return;
		current.beginFrame(graphics.guiWidth(), graphics.guiHeight(), MouseUtil.getX(), MouseUtil.getY());
		rendering = true;
		try {
			var platform = Services.PLATFORM.getRenderHelper();
			current.setClip(platform.getTooltipClip(graphics));
			platform.renderPreparedTooltip(graphics, font, current.components(), x, y, positioner, stack);
		} finally {
			rendering = false;
		}
	}

	public void tick() {
		update();
		if (session != null)
			session.tick();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.isWindowActive()) {
			long window = minecraft.getWindow().getWindow();
			pendingReleases.removeIf(button -> GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_RELEASE);
			pendingKeyReleases.removeIf(key -> key >= 0 && !InputConstants.isKeyDown(window, key));
		}
	}

	private void update() {
		if (enabled && !Internal.getKeyMappings().getPauseRecipeCycling().isDown())
			pinBlocked = false;
		if (session == null)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (!session.isActive())
			close(TooltipCloseReason.REQUESTED);
		else if (minecraft.screen != screen)
			close(TooltipCloseReason.SCREEN_CHANGED);
		else if (!minecraft.isWindowActive())
			close(TooltipCloseReason.FOCUS_LOST);
		else if (!sourceValid.getAsBoolean())
			close(TooltipCloseReason.SOURCE_INVALIDATED);
		else if (!Internal.getKeyMappings().getPauseRecipeCycling().isDown())
			close(TooltipCloseReason.PIN_RELEASED);
		else
			session.updateMouse(MouseUtil.getX(), MouseUtil.getY());
	}

	public void ownerClosed(IPinnedTooltipHolder holder) {
		if (attachedOwner == holder)
			close(TooltipCloseReason.SOURCE_INVALIDATED);
	}

	@Override
	public void hide() { close(TooltipCloseReason.REPLACED); }

	public void close(TooltipCloseReason reason) {
		TooltipInteractionSession previous = session;
		IPinnedTooltipHolder owner = attachedOwner;
		if (previous != null)
			pinBlocked = reason != TooltipCloseReason.PIN_RELEASED;
		session = null;
		sourceValid = () -> true;
		attachedOwner = null;
		attachedPositioner = null;
		screen = null;
		font = null;
		stack = null;
		width = height = 0;
		PinnedTooltipManager.closed(this);
		if (previous != null) {
			pendingReleases.addAll(previous.consumedButtons());
			previous.close(reason);
		}
		if (owner != null)
			owner.hide();
	}

	private boolean inside(double x, double y) {
		return session != null && attachedOwner == null && width > 0 && x >= left - 4 && x < left + width + 4 && y >= top - 4 && y < top + height + 4;
	}

	/** A standalone JEI screen may own this release instead of the global input controller. */
	public boolean isMouseReleaseRouted(int button) {
		return routedButtons.contains(button);
	}

	public boolean consumeStoppedRelease(InputConstants.Key key) {
		if (enabled) {
			return false;
		}
		return key.getType() == InputConstants.Type.MOUSE ? pendingReleases.remove(key.getValue()) : pendingKeyReleases.remove(key.getValue());
	}

	public boolean mousePressed(double x, double y, int button, int modifiers) {
		update();
		if (session == null)
			return false;
		boolean consumed = session.mousePressed(x, y, button, modifiers) || inside(x, y);
		if (consumed)
			pendingReleases.add(button);
		return consumed;
	}

	public boolean mouseReleased(double x, double y, int button, int modifiers) {
		update();
		boolean consumed = pendingReleases.remove(button);
		if (session != null)
			consumed |= session.mouseReleased(x, y, button, modifiers);
		return consumed;
	}

	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		update();
		return (session != null && session.mouseDragged(x, y, button, dx, dy)) || pendingReleases.contains(button);
	}

	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		update();
		return session != null && (session.mouseScrolled(x, y, dx, dy) || inside(x, y));
	}

	public boolean keyPressed(int key, int scan, int modifiers) {
		update();
		if (session == null)
			return false;
		boolean consumed;
		if (key == GLFW.GLFW_KEY_ESCAPE || Internal.getKeyMappings().getCloseRecipeGui().matchesIgnoringModifiers(InputConstants.getKey(key, scan))) {
			close(TooltipCloseReason.ESCAPE);
			consumed = true;
		} else
			consumed = session.keyPressed(key, scan, modifiers);
		if (consumed)
			pendingKeyReleases.add(key);
		return consumed;
	}

	public boolean keyReleased(int key, int scan, int modifiers) {
		if (session != null && Internal.getKeyMappings().getPauseRecipeCycling().isActiveAndMatchesAllowingExtraModifiers(InputConstants.getKey(key, scan))) {
			close(TooltipCloseReason.PIN_RELEASED);
		}
		update();
		boolean consumed = pendingKeyReleases.remove(key);
		if (session != null)
			consumed |= session.keyReleased(key, scan, modifiers);
		return consumed;
	}

	public boolean charTyped(char character, int modifiers) {
		update();
		return session != null && session.charTyped(character, modifiers);
	}
}

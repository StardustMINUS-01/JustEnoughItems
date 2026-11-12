package mezz.jei.gui.input;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.tooltip.IInteractiveClientTooltipComponent;
import mezz.jei.api.gui.tooltip.TooltipCloseReason;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.GuiRenderLayers;
import mezz.jei.common.gui.ITooltipStackAccessInternal;
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

/** Single client tooltip host. Platform hooks call this before ordinary input routing. */
public final class InteractiveTooltipController implements IPinnedTooltipHolder {
	public static final InteractiveTooltipController INSTANCE = new InteractiveTooltipController();
	private @Nullable TooltipInteractionSession session;
	private @Nullable Screen screen;
	private @Nullable IPinnedTooltipHolder attachedOwner;
	private @Nullable Font font;
	private @Nullable ClientTooltipPositioner attachedPositioner;
	private ItemStack stack = ItemStack.EMPTY;
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
	private final Set<Integer> pendingKeyReleases = new HashSet<>();

	private InteractiveTooltipController() {}

	public void install() { close(TooltipCloseReason.RUNTIME_STOPPED); enabled = true; }
	public void stop() { close(TooltipCloseReason.RUNTIME_STOPPED); enabled = false; }
	public boolean hasKeyboardFocus() { return session != null && session.hasFocus(); }
	public boolean isRendering() { return rendering; }

	public boolean intercept(GuiGraphics graphics, Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner) {
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
				((ITooltipStackAccessInternal) graphics).jei$renderPreparedTooltip(font, new java.util.ArrayList<>(created.components()), x, y, positioner);
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
		this.stack = graphics instanceof ITooltipStackAccessInternal access ? access.jei$getTooltipStack().copy() : ItemStack.EMPTY;
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
		if (current == null || font == null)
			return;
		current.beginFrame(graphics.guiWidth(), graphics.guiHeight(), MouseUtil.getX(), MouseUtil.getY());
		rendering = true;
		ItemStack previous = graphics instanceof ITooltipStackAccessInternal access ? access.jei$getTooltipStack() : ItemStack.EMPTY;
		try {
			if (graphics instanceof ITooltipStackAccessInternal access)
				access.jei$setTooltipStack(stack);
			((ITooltipStackAccessInternal) graphics).jei$renderPreparedTooltip(font, new java.util.ArrayList<>(current.components()), x, y, positioner);
		} finally {
			if (graphics instanceof ITooltipStackAccessInternal access)
				access.jei$setTooltipStack(previous);
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
		stack = ItemStack.EMPTY;
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
		if (key == GLFW.GLFW_KEY_ESCAPE || mezz.jei.common.input.keys.IJeiKeyMappingWithExtraModifiers.matchesShortcut(Internal.getKeyMappings().getCloseRecipeGui(), InputConstants.getKey(key, scan))) {
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

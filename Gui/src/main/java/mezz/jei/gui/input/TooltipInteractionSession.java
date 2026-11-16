package mezz.jei.gui.input;

import mezz.jei.api.gui.tooltip.IInteractiveClientTooltipComponent;
import mezz.jei.api.gui.tooltip.ITooltipInteractionContext;
import mezz.jei.api.gui.tooltip.TooltipCloseReason;
import mezz.jei.api.gui.tooltip.TooltipPointerResult;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Client-thread interaction state, independent of GLFW and platform event types. */
public final class TooltipInteractionSession {
	private @Nullable ScreenRectangle clip;

	void setClip(@Nullable ScreenRectangle clip) {
		this.clip = clip;
	}
	private final List<Entry> entries = new ArrayList<>();
	private final List<ClientTooltipComponent> components;
	private final Map<Integer, Entry> pressed = new HashMap<>();
	private final Set<Integer> consumedButtons = new HashSet<>();
	private final @Nullable ITypedIngredient<?> ingredient;
	private final boolean pinned;
	private boolean active;
	private @Nullable Entry focused;
	private @Nullable Entry captured;
	private int capturedButton = -1;
	private double mouseX;
	private double mouseY;
	private int screenWidth;
	private int screenHeight;

	public TooltipInteractionSession(List<ClientTooltipComponent> source, @Nullable ITypedIngredient<?> ingredient, boolean pinned) {
		this.ingredient = ingredient;
		this.pinned = pinned;
		this.active = pinned;
		List<ClientTooltipComponent> wrapped = new ArrayList<>(source.size());
		for (ClientTooltipComponent component : source) {
			if (component instanceof IInteractiveClientTooltipComponent interactive) {
				Entry entry = new Entry(interactive);
				entries.add(entry);
				wrapped.add(entry);
			} else {
				wrapped.add(component);
			}
		}
		components = List.copyOf(wrapped);
	}

	public void mount() {
		if (!active)
			return;
		for (Entry entry : entries) {
			if (!active)
				break;
			entry.mounted = true;
			entry.component.onMounted(entry);
			if (active)
				entry.component.onPinnedChanged(entry);
		}
	}

	public List<ClientTooltipComponent> components() { return components; }
	public boolean isActive() { return active; }
	public boolean hasFocus() { return active && focused != null; }
	public boolean hasCapture() { return active && captured != null; }
	public Set<Integer> consumedButtons() { return Set.copyOf(consumedButtons); }

	public void updateMouse(double x, double y) { mouseX = x; mouseY = y; }

	public void beginFrame(int width, int height, double x, double y) {
		screenWidth = width;
		screenHeight = height;
		updateMouse(x, y);
		entries.forEach(entry -> entry.valid = false);
	}

	public void tick() {
		for (Entry entry : entries) {
			if (!active)
				break;
			entry.component.onTick(entry);
		}
	}

	public boolean isMouseOver(double x, double y) {
		return active && hit(x, y) != null;
	}

	public void clearFocus() {
		focus(null);
	}

	public void cancelInteraction(int button) {
		Entry entry = pressed.remove(button);
		if (capturedButton == button) {
			captured = null;
			capturedButton = -1;
		}
		if (active && entry != null) {
			entry.component.onInteractionCancelled(entry);
		}
	}

	public boolean mousePressed(double x, double y, int button, int modifiers) {
		updateMouse(x, y);
		if (!active)
			return false;
		Entry entry = captured != null ? captured : hit(x, y);
		if (entry == null) {
			focus(null);
			return false;
		}
		consumedButtons.add(button);
		TooltipPointerResult result = entry.component.mousePressed(entry, button, modifiers);
		if (!active)
			return true;
		if (result != TooltipPointerResult.PASS) {
			pressed.put(button, entry);
			if (entry.component.isFocusable())
				focus(entry);
			if (result == TooltipPointerResult.CAPTURE && captured == null) {
				captured = entry;
				capturedButton = button;
			}
		}
		// An interactive component is an opaque input surface, even for unhandled gestures.
		return true;
	}

	public boolean mouseReleased(double x, double y, int button, int modifiers) {
		updateMouse(x, y);
		boolean consume = consumedButtons.remove(button);
		Entry entry = pressed.remove(button);
		if (capturedButton == button) {
			captured = null;
			capturedButton = -1;
		}
		if (active && entry != null)
			entry.component.mouseReleased(entry, button, modifiers);
		return consume;
	}

	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		updateMouse(x, y);
		if (!active)
			return false;
		if (captured != null && capturedButton == button) {
			captured.component.mouseDragged(captured, button, dx, dy);
			return true;
		}
		Entry entry = pressed.get(button);
		if (entry != null && hit(x, y) == entry)
			entry.component.mouseDragged(entry, button, dx, dy);
		return consumedButtons.contains(button);
	}

	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		updateMouse(x, y);
		if (!active)
			return false;
		Entry entry = captured != null ? captured : hit(x, y);
		if (entry == null)
			return false;
		entry.component.mouseScrolled(entry, dx, dy);
		return true;
	}

	public boolean keyPressed(int key, int scan, int modifiers) {
		if (!active)
			return false;
		if (focused != null && focused.component.keyPressed(focused, key, scan, modifiers))
			return true;
		if (!active)
			return true;
		if (key == 258) { // GLFW Tab: platform-independent session does not query GLFW state.
			List<Entry> focusable = entries.stream().filter(e -> e.valid && e.component.isFocusable()).toList();
			if (!focusable.isEmpty()) {
				int index = focusable.indexOf(focused);
				int next = (modifiers & 1) != 0 ? (index < 0 ? focusable.size() - 1 : Math.floorMod(index - 1, focusable.size())) : (index + 1) % focusable.size();
				focus(focusable.get(next));
				return true;
			}
		}
		return hasFocus() || hasCapture() || hit(mouseX, mouseY) != null;
	}

	public boolean keyReleased(int key, int scan, int modifiers) {
		if (hasFocus()) {
			focused.component.keyReleased(focused, key, scan, modifiers);
			return true;
		}
		return hasCapture();
	}

	public boolean charTyped(char character, int modifiers) {
		if (!hasFocus())
			return hasCapture();
		focused.component.charTyped(focused, character, modifiers);
		return true;
	}

	private @Nullable Entry hit(double x, double y) {
		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry e = entries.get(i);
			ScreenRectangle area = e.getVisibleBounds();
			if (e.valid && x >= area.left() && x < area.right() && y >= area.top() && y < area.bottom())
				return e;
		}
		return null;
	}

	private void focus(@Nullable Entry entry) {
		if (!active || focused == entry)
			return;
		Entry previous = focused;
		focused = entry;
		if (previous != null)
			previous.component.onFocusChanged(previous);
		if (active && focused == entry && entry != null)
			entry.component.onFocusChanged(entry);
	}

	public void close(TooltipCloseReason reason) {
		if (!active)
			return;
		active = false;
		Set<Entry> interrupted = new HashSet<>(pressed.values());
		if (captured != null)
			interrupted.add(captured);
		pressed.clear();
		captured = null;
		capturedButton = -1;
		Entry previous = focused;
		focused = null;
		for (Entry entry : interrupted)
			entry.component.onInteractionCancelled(entry);
		if (previous != null)
			previous.component.onFocusChanged(previous);
		for (Entry entry : entries) {
			entry.valid = false;
			if (entry.mounted) {
				entry.component.onPinnedChanged(entry);
				entry.component.onClosed(entry, reason);
			}
		}
	}

	private final class Entry implements ClientTooltipComponent, ITooltipInteractionContext {
		final IInteractiveClientTooltipComponent component;
		ScreenRectangle bounds = new ScreenRectangle(0, 0, 0, 0);
		ScreenRectangle visibleBounds = bounds;
		boolean valid;
		boolean mounted;
		Entry(IInteractiveClientTooltipComponent component) { this.component = component; }
		@Override
		public int getWidth(Font font) { return component.getWidth(font); }
		@Override
		public int getHeight() { return component.getHeight(); }
		@Override
		public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffers) {
			layout(font, x, y, null);
			if (!pinned || active)
				component.renderText(font, x, y, matrix, buffers);
		}
		@Override
		public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
			layout(font, x, y, clip);
			if (!pinned || active)
				component.renderImage(font, x, y, graphics);
		}

		private void layout(Font font, int x, int y, @Nullable ScreenRectangle clip) {
			ScreenRectangle next = new ScreenRectangle(x, y, Math.max(0, getWidth(font)), Math.max(0, getHeight()));
			ScreenRectangle visible = next.intersection(new ScreenRectangle(0, 0, screenWidth, screenHeight));
			if (visible != null && clip != null)
				visible = visible.intersection(clip);
			ScreenRectangle nextVisible = visible == null ? new ScreenRectangle(0, 0, 0, 0) : visible;
			boolean changed = !valid || !next.equals(bounds) || !nextVisible.equals(visibleBounds);
			bounds = next;
			visibleBounds = nextVisible;
			valid = true;
			if (changed && (!pinned || active))
				component.onLayout(this);
		}
		@Override
		public ScreenRectangle getBounds() { return bounds; }
		@Override
		public ScreenRectangle getVisibleBounds() {
			return visibleBounds;
		}
		@Override
		public double getMouseX() { return mouseX - bounds.left(); }
		@Override
		public double getMouseY() { return mouseY - bounds.top(); }
		@Override
		public boolean isPinned() { return pinned && active; }
		@Override
		public boolean isFocused() { return active && focused == this; }
		@Override
		public boolean isMouseCaptured() { return active && captured == this; }
		@Override
		public boolean isActive() { return active; }
		@Override
		public Optional<ITypedIngredient<?>> getIngredient() { return Optional.ofNullable(ingredient); }
		@Override
		public void requestFocus() {
			if (component.isFocusable())
				focus(this);
		}
		@Override
		public void releaseFocus() {
			if (focused == this)
				focus(null);
		}
		@Override
		public void releaseMouseCapture() {
			if (captured == this) {
				captured = null;
				capturedButton = -1;
			}
		}
		@Override
		public void requestLayout() { entries.forEach(e -> e.valid = false); }
		@Override
		public void close() { TooltipInteractionSession.this.close(TooltipCloseReason.REQUESTED); }
	}
}

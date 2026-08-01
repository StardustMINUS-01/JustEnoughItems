package mezz.jei.gui.input.handlers;

import mezz.jei.gui.input.IDragHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class DragRouter {
	private final List<IDragHandler> handlers;
	@Nullable
	private IDragHandler dragStartedCallback;
	@Nullable
	private UserInput pendingDragStartInput;

	public DragRouter(IDragHandler... handlers) {
		this.handlers = List.of(handlers);
	}

	public void handleGuiChange() {
		cancelDrag();
	}

	public boolean startDrag(Screen screen, UserInput input) {
		cancelDrag();
		this.pendingDragStartInput = input;

		this.dragStartedCallback = this.handlers.stream()
			.map(i -> i.handleDragStart(screen, input))
			.flatMap(Optional::stream)
			.findFirst()
			.orElse(null);

		return this.dragStartedCallback != null;
	}

	public boolean startDragIfIdle(Screen screen, UserInput input) {
		if (this.dragStartedCallback != null) {
			return false;
		}
		UserInput startInput = this.pendingDragStartInput == null ? input : this.pendingDragStartInput;
		this.dragStartedCallback = this.handlers.stream()
			.map(i -> i.handleDragStart(screen, startInput))
			.flatMap(Optional::stream)
			.findFirst()
			.orElse(null);

		return this.dragStartedCallback != null;
	}

	public boolean completeDrag(Screen screen, UserInput input) {
		if (this.dragStartedCallback == null) {
			this.pendingDragStartInput = null;
			return false;
		}
		boolean result = this.dragStartedCallback.handleDragComplete(screen, input);
		this.dragStartedCallback = null;
		this.pendingDragStartInput = null;
		return result;
	}

	public void cancelDrag() {
		for (IDragHandler handler : this.handlers) {
			handler.handleDragCanceled();
		}
		this.pendingDragStartInput = null;
		this.dragStartedCallback = null;
	}
}

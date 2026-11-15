package mezz.jei.gui.input.handlers;

import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.IRecipeFocusSource;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IDraggableIngredientInternal;
import java.util.stream.Stream;
import mezz.jei.common.input.IGuiInputLayer;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.UserInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import java.util.Optional;
import java.util.function.Supplier;

public final class ProxyGuiInputLayer implements IGuiInputLayer, IRecipeFocusSource {
	private final Supplier<IGuiInputLayer> delegate;
	public ProxyGuiInputLayer(Supplier<IGuiInputLayer> delegate) { this.delegate = delegate; }
	@Override
	public boolean isMouseOver(double x, double y) { return delegate.get().isMouseOver(x, y); }
	@Override
	public void update(double x, double y) { delegate.get().update(x, y); }
	@Override
	public void draw(GuiGraphics graphics, int x, int y) { delegate.get().draw(graphics, x, y); }
	@Override
	public void resetInput() { delegate.get().resetInput(); }
	@Override
	public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keys) {
		return delegate.get().beginInput(screen, input, keys);
	}
	@Override
	public boolean scroll(double x, double y, double dx, double dy) {
		return delegate.get().scroll(x, y, dx, dy);
	}
	@Override
	public Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(double x, double y) {
		return delegate.get() instanceof IRecipeFocusSource source ? source.getIngredientUnderMouse(x, y) : Stream.empty();
	}

	@Override
	public Stream<IDraggableIngredientInternal<?>> getDraggableIngredientUnderMouse(double x, double y) {
		return delegate.get() instanceof IRecipeFocusSource source ? source.getDraggableIngredientUnderMouse(x, y) : Stream.empty();
	}

}

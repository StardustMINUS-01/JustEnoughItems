package mezz.jei.gui.input.handlers;

import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.IGuiInputLayer;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import java.util.Optional;
import java.util.function.Supplier;

public final class ProxyGuiInputLayer implements IGuiInputLayer {
	private final Supplier<IGuiInputLayer> delegate;
	public ProxyGuiInputLayer(Supplier<IGuiInputLayer> delegate) { this.delegate = delegate; }
	@Override
	public boolean isMouseOver(double x, double y) { return delegate.get().isMouseOver(x, y); }
	@Override
	public void update(double x, double y) { delegate.get().update(x, y); }
	@Override
	public void draw(GuiGraphics graphics, int x, int y) { delegate.get().draw(graphics, x, y); }
	@Override
	public void unfocus() { delegate.get().unfocus(); }
	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keys) {
		return delegate.get().handleUserInput(screen, input, keys);
	}
	@Override
	public Optional<IUserInputHandler> handleMouseScrolled(double x, double y, double dx, double dy) {
		return delegate.get().handleMouseScrolled(x, y, dx, dy);
	}

	@Override
	public Optional<IUserInputHandler> handleMouseDragged(double x, double y, com.mojang.blaze3d.platform.InputConstants.Key key, double dx, double dy) {
		return delegate.get().handleMouseDragged(x, y, key, dx, dy);
	}
}

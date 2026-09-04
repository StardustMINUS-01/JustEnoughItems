package mezz.jei.gui.startup;

import mezz.jei.gui.events.GuiEventHandler;
import mezz.jei.gui.input.ClientInputHandler;
import mezz.jei.gui.input.handlers.WorldInputHandler;

public record JeiEventHandlers(
	GuiEventHandler guiEventHandler,
	ClientInputHandler clientInputHandler,
	WorldInputHandler worldInputHandler,
	ResourceReloadHandler resourceReloadHandler
) {
}

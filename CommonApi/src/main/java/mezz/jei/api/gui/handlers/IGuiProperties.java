package mezz.jei.api.gui.handlers;

import net.minecraft.client.gui.screens.Screen;

/**
 * Defines the properties of a gui so that JEI can draw next to it.
 * Created by {@link IScreenHandler#apply(Screen)}
 */
public interface IGuiProperties {
	Class<? extends Screen> screenClass();

	int guiLeft();

	int guiTop();

	int guiXSize();

	int guiYSize();

	int screenWidth();

	int screenHeight();

	default int getGuiLeft() {
		return guiLeft();
	}

	default int getGuiTop() {
		return guiTop();
	}

	default int getGuiXSize() {
		return guiXSize();
	}

	default int getGuiYSize() {
		return guiYSize();
	}

	default int getScreenWidth() {
		return screenWidth();
	}

	default int getScreenHeight() {
		return screenHeight();
	}
}

package mezz.jei.neoforge.compat.ae2;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.implementations.CellWorkbenchScreen;
import appeng.client.gui.implementations.IOBusScreen;
import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import mezz.jei.gui.plugins.JeiGuiPlugin;

public final class Ae2JeiSearchTextCompat {
	private Ae2JeiSearchTextCompat() {
	}

	public static void register() {
		if (!Ae2CompatUtil.isLoaded()) {
			return;
		}
		JeiGuiPlugin.addGuiHandlerHook(registration -> {
			registration.addGhostIngredientHandler(MEStorageScreen.class, new JeiSearchTextGhostIngredientHandler<>());
			registration.addGhostIngredientHandler(PatternAccessTermScreen.class, new JeiSearchTextGhostIngredientHandler<>());
			Ae2ConfigGhostIngredientHandler<CellWorkbenchScreen> cellWorkbenchHandler = new Ae2ConfigGhostIngredientHandler<>();
			registration.addGhostIngredientHandler(CellWorkbenchScreen.class, cellWorkbenchHandler);
			Ae2ConfigGhostIngredientHandler<IOBusScreen> ioBusHandler = new Ae2ConfigGhostIngredientHandler<>();
			registration.addGhostIngredientHandler(IOBusScreen.class, ioBusHandler);
		});
	}
}

package mezz.jei.gui.compat;

import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.Screen;

public final class ExternalIngredientSearchHandlerRegistry {
	private static Handler handler = (screen, ingredient, simulate) -> false;

	private ExternalIngredientSearchHandlerRegistry() {
	}

	public static boolean search(Screen screen, ITypedIngredient<?> ingredient, boolean simulate) {
		return handler.search(screen, ingredient, simulate);
	}

	public static void register(Handler handler) {
		ExternalIngredientSearchHandlerRegistry.handler = handler;
	}

	@FunctionalInterface
	public interface Handler {
		boolean search(Screen screen, ITypedIngredient<?> ingredient, boolean simulate);
	}
}

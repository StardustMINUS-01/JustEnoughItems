package mezz.jei.common.gui;

import mezz.jei.api.ingredients.ITypedIngredient;
import org.jetbrains.annotations.Nullable;

/** Restricts the platform tooltip hook to draws originating from JEI. */
public final class TooltipRenderScope {
	private static final ThreadLocal<TooltipRenderScope> CURRENT = new ThreadLocal<>();
	private final @Nullable ITypedIngredient<?> ingredient;
	private final java.util.function.BooleanSupplier sourceValid;

	private TooltipRenderScope(@Nullable ITypedIngredient<?> ingredient, java.util.function.BooleanSupplier sourceValid) { this.ingredient = ingredient; this.sourceValid = sourceValid; }
	public java.util.function.BooleanSupplier sourceValid() { return sourceValid; }

	public static @Nullable TooltipRenderScope current() { return CURRENT.get(); }
	public @Nullable ITypedIngredient<?> ingredient() { return ingredient; }

	public static void draw(@Nullable ITypedIngredient<?> ingredient, java.util.function.BooleanSupplier sourceValid, Runnable draw) {
		TooltipRenderScope previous = CURRENT.get();
		CURRENT.set(new TooltipRenderScope(ingredient, sourceValid));
		try {
			draw.run();
		} finally {
			if (previous == null)
				CURRENT.remove();
			else
				CURRENT.set(previous);
		}
	}
}

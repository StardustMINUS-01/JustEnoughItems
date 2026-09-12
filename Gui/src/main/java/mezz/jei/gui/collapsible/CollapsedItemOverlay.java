package mezz.jei.gui.collapsible;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.util.SafeIngredientUtil;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the collapsed group double stack: the background stack at a lower z
 * and the representative stack at a higher z, with the GTNH NEI offsets.
 */
public final class CollapsedItemOverlay implements IDrawable {
	private static final int BACKGROUND_X = 0;
	private static final int BACKGROUND_Y = -2;
	private static final int FOREGROUND_X = -3;
	private static final int FOREGROUND_Y = 1;
	private static final float BACKGROUND_Z = -0.5F;
	private static final float FOREGROUND_Z = 0.5F;
	private final ITypedIngredient<?> foreground;
	private final ITypedIngredient<?> background;

	public CollapsedItemOverlay(ITypedIngredient<?> foreground, ITypedIngredient<?> background) {
		this.foreground = foreground;
		this.background = background;
	}

	@Override
	public int getWidth() {
		return 0;
	}

	@Override
	public int getHeight() {
		return 0;
	}

	@Override
	public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
		drawStack(guiGraphics, background, xOffset + BACKGROUND_X, yOffset + BACKGROUND_Y, BACKGROUND_Z);
		drawStack(guiGraphics, foreground, xOffset + FOREGROUND_X, yOffset + FOREGROUND_Y, FOREGROUND_Z);
	}

	private static void drawStack(
		GuiGraphics guiGraphics,
		ITypedIngredient<?> typedIngredient,
		int x,
		int y,
		float z
	) {
		IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
		@SuppressWarnings("unchecked")
		IIngredientRenderer<Object> renderer = (IIngredientRenderer<Object>) ingredientManager.getIngredientRenderer(typedIngredient.getType());
		@SuppressWarnings("unchecked")
		IIngredientType<Object> type = (IIngredientType<Object>) typedIngredient.getType();
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(0, 0, z);
		SafeIngredientUtil.render(guiGraphics, renderer, type, typedIngredient.getIngredient(), x, y);
		guiGraphics.pose().popPose();
	}
}

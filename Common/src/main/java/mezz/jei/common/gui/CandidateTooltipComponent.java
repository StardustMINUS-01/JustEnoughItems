package mezz.jei.common.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.util.MathUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;

public class CandidateTooltipComponent<T> implements ClientTooltipComponent, TooltipComponent {
	private static final int MAX_PER_LINE = 10;
	private static final int MAX_LINES = 3;
	private static final int MAX_INGREDIENTS = MAX_PER_LINE * MAX_LINES;
	private static final int INGREDIENT_SIZE = 18;
	private static final int INGREDIENT_PADDING = 1;

	private final List<RenderElement<?>> ingredients;
	private final int selectedIndex;
	private final int windowStart;

	public CandidateTooltipComponent(IIngredientRenderer<T> renderer, List<T> ingredients) {
		this(renderer, ingredients, -1, 0);
	}

	public CandidateTooltipComponent(IIngredientRenderer<T> renderer, List<T> ingredients, int selectedIndex, int windowStart) {
		this(
			ingredients.stream()
				.<RenderElement<?>>map(ingredient -> RenderElement.create(renderer, ingredient))
				.toList(),
			selectedIndex,
			windowStart
		);
	}

	public static CandidateTooltipComponent<?> create(
		IIngredientManager ingredientManager,
		List<ITypedIngredient<?>> ingredients,
		int selectedIndex,
		int windowStart
	) {
		List<RenderElement<?>> renderElements = ingredients.stream()
			.<RenderElement<?>>map(ingredient -> RenderElement.create(ingredientManager, ingredient))
			.toList();
		return new CandidateTooltipComponent<>(renderElements, selectedIndex, windowStart);
	}

	private CandidateTooltipComponent(List<RenderElement<?>> ingredients, int selectedIndex, int windowStart) {
		this.ingredients = ingredients;
		this.selectedIndex = selectedIndex;
		this.windowStart = windowStart;
	}

	@Override
	public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
		int visibleCandidateCount = CandidateTooltipWindow.getVisibleCandidateCount(ingredients.size());
		int visibleStart = CandidateTooltipWindow.updateStart(ingredients.size(), selectedIndex, windowStart);
		drawIngredients(guiGraphics, x, y, visibleStart, visibleCandidateCount);
		if (ingredients.size() > MAX_INGREDIENTS) {
			int remainingCount = Math.min(ingredients.size() - visibleCandidateCount, 99);
			String countString = "+" + remainingCount;
			int textHeight = font.lineHeight - 1;
			int textWidth = font.width(countString);
			int textCenterX = x + (MAX_PER_LINE - 1) * INGREDIENT_SIZE + ((INGREDIENT_SIZE - textWidth) / 2);
			int textCenterY = y + (MAX_LINES - 1) * INGREDIENT_SIZE + ((INGREDIENT_SIZE - textHeight) / 2);
			guiGraphics.drawString(font, countString, textCenterX, textCenterY, 0xFFAAAAAA);
		}
	}

	private void drawIngredients(GuiGraphics guiGraphics, int x, int y, int start, int count) {
		int maxPerLine = MathUtil.divideCeil(count, getLineCount());
		for (int i = 0; i < count; i++) {
			int column = i % maxPerLine;
			int row = i / maxPerLine;
			int ingredientIndex = start + i;
			PoseStack poseStack = guiGraphics.pose();
			poseStack.pushPose();
			{
				poseStack.translate(x + column * INGREDIENT_SIZE + INGREDIENT_PADDING, y + row * INGREDIENT_SIZE + INGREDIENT_PADDING, 0.0D);
				ingredients.get(ingredientIndex).render(guiGraphics);
				if (ingredientIndex == selectedIndex) {
					guiGraphics.fillGradient(RenderType.guiOverlay(), 0, 0, 16, 16, 0x59FFFFFF, 0x59FFFFFF, 0);
				}
			}
			poseStack.popPose();
		}
	}

	private int getLineCount() {
		int lineCount = MathUtil.divideCeil(ingredients.size(), MAX_PER_LINE);
		return Math.min(lineCount, MAX_LINES);
	}

	private int getMaxPerLine() {
		int perLine = MathUtil.divideCeil(ingredients.size(), getLineCount());
		return Math.min(perLine, MAX_PER_LINE);
	}

	@Override
	public int getHeight() {
		return getLineCount() * INGREDIENT_SIZE + (2 * INGREDIENT_PADDING);
	}

	@Override
	public int getWidth(Font font) {
		return getMaxPerLine() * INGREDIENT_SIZE + (2 * INGREDIENT_PADDING);
	}

	private record RenderElement<T>(IIngredientRenderer<T> renderer, T ingredient) {
		public static <T> RenderElement<?> create(IIngredientRenderer<T> renderer, T ingredient) {
			return new RenderElement<>(renderer, ingredient);
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		public static RenderElement<?> create(IIngredientManager ingredientManager, ITypedIngredient<?> typedIngredient) {
			IIngredientType ingredientType = typedIngredient.getType();
			IIngredientRenderer renderer = ingredientManager.getIngredientRenderer(ingredientType);
			return new RenderElement<>(renderer, typedIngredient.getIngredient());
		}

		public void render(GuiGraphics guiGraphics) {
			renderer.render(guiGraphics, ingredient);
		}
	}
}

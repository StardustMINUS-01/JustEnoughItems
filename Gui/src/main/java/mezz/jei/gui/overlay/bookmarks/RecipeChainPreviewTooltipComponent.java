package mezz.jei.gui.overlay.bookmarks;

import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.platform.Services;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipModel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;
import java.util.Optional;

public class RecipeChainPreviewTooltipComponent implements ClientTooltipComponent, TooltipComponent {
	private static final int MAX_INGREDIENTS_PER_ROW = 16;
	private static final int INGREDIENT_SIZE = 18;
	private static final int INGREDIENT_PADDING = 1;
	private static final float AMOUNT_TEXT_SCALE = 0.65f;

	private final List<RenderElement<?>> ingredients;

	public RecipeChainPreviewTooltipComponent(List<RecipeChainTooltipModel.Item> items) {
		IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
		this.ingredients = items.stream()
			.map(item -> RenderElement.create(item, ingredientManager))
			.flatMap(Optional::stream)
			.toList();
	}

	public boolean isEmpty() {
		return ingredients.isEmpty();
	}

	@Override
	public int getHeight() {
		return 4 + INGREDIENT_SIZE * MathUtil.divideCeil(ingredients.size(), MAX_INGREDIENTS_PER_ROW);
	}

	@Override
	public int getWidth(Font font) {
		return INGREDIENT_SIZE * Math.min(ingredients.size(), MAX_INGREDIENTS_PER_ROW);
	}

	@Override
	public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
		for (int i = 0; i < ingredients.size(); i++) {
			int elementX = INGREDIENT_PADDING + x + ((i % MAX_INGREDIENTS_PER_ROW) * INGREDIENT_SIZE);
			int elementY = INGREDIENT_PADDING + y + ((i / MAX_INGREDIENTS_PER_ROW) * INGREDIENT_SIZE);
			RenderElement<?> renderElement = ingredients.get(i);
			PoseStack pose = guiGraphics.pose();
			pose.pushPose();
			{
				pose.translate(elementX, elementY, 0);
				renderElement.render(guiGraphics, font);
			}
			pose.popPose();
		}
	}

	private record RenderElement<T>(
		IIngredientRenderer<T> renderer,
		T ingredient,
		Optional<String> amountText
	) {
		@SuppressWarnings({"unchecked", "rawtypes"})
		public static Optional<RenderElement<?>> create(
			RecipeChainTooltipModel.Item item,
			IIngredientManager ingredientManager
		) {
			BookmarkIngredientKey key = item.key();
			Optional<ITypedIngredient<?>> resolved = Optional.ofNullable(item.ingredient());
			return resolved.or(() -> Optional.ofNullable(key.typedIngredient()))
				.map(typedIngredient -> create(item, (ITypedIngredient) typedIngredient, ingredientManager));
		}

		private static <T> RenderElement<T> create(
			RecipeChainTooltipModel.Item item,
			ITypedIngredient<T> typedIngredient,
			IIngredientManager ingredientManager
		) {
			IIngredientType<T> type = typedIngredient.getType();
			IIngredientHelper<T> helper = ingredientManager.getIngredientHelper(type);
			IIngredientRenderer<T> renderer = ingredientManager.getIngredientRenderer(type);
			boolean fluidAmountUnits = usesFluidAmountUnits(type);
			T renderIngredient = copyForPreview(item.amount(), typedIngredient, helper, fluidAmountUnits);
			Optional<String> amountText = getAmountText(item.amount(), type.getUid());
			return new RenderElement<>(renderer, renderIngredient, amountText);
		}

		private static <T> T copyForPreview(
			long amount,
			ITypedIngredient<T> typedIngredient,
			IIngredientHelper<T> helper,
			boolean fluidAmountUnits
		) {
			if (fluidAmountUnits || amount <= 0) {
				return typedIngredient.getIngredient();
			}
			return helper.copyWithAmount(typedIngredient.getIngredient(), 1);
		}

		private static <T> boolean usesFluidAmountUnits(IIngredientType<T> type) {
			return type.equals(Services.PLATFORM.getFluidHelper().getFluidIngredientType()) ||
				BookmarkAmountFormatter.usesFluidAmountUnits(type.getUid());
		}

		private static Optional<String> getAmountText(long amount, String ingredientTypeUid) {
			if (amount <= 1) {
				return Optional.empty();
			}
			return Optional.of(BookmarkAmountFormatter.formatTypedAmount(amount, ingredientTypeUid));
		}

		public void render(GuiGraphics guiGraphics, Font font) {
			renderer.render(guiGraphics, ingredient);
			amountText.ifPresent(text -> drawAmountText(guiGraphics, font, text));
		}

		private static void drawAmountText(GuiGraphics guiGraphics, Font font, String text) {
			int x = INGREDIENT_SIZE - Math.round(font.width(text) * AMOUNT_TEXT_SCALE);
			int y = INGREDIENT_SIZE - Math.round(font.lineHeight * AMOUNT_TEXT_SCALE);
			PoseStack pose = guiGraphics.pose();
			pose.pushPose();
			pose.translate(x, y, 300);
			pose.scale(AMOUNT_TEXT_SCALE, AMOUNT_TEXT_SCALE, 1);
			guiGraphics.drawString(font, text, 0, 0, 0xFFFFFFFF, true);
			pose.popPose();
		}
	}
}

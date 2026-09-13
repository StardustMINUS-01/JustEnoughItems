package mezz.jei.gui.chat;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.common.chat.JeiChatItemLinkHover;
import mezz.jei.common.chat.JeiChatItemLinks;
import mezz.jei.common.chat.JeiChatItemLinks.IngredientLink;
import mezz.jei.common.chat.SharedChatIngredient;
import net.minecraft.client.Minecraft;
import java.util.Objects;
import mezz.jei.common.gui.IngredientTooltipComponent;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.util.SafeIngredientUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class ChatIngredientTooltip {
	private static @Nullable Style sharedStyle;
	private static @Nullable IJeiRuntime sharedRuntime;
	private static Optional<ITypedIngredient<?>> sharedIngredient = Optional.empty();

	private ChatIngredientTooltip() {
	}

	public static void clearSharedIngredient() {
		sharedStyle = null;
		sharedRuntime = null;
		sharedIngredient = Optional.empty();
	}

	public static Optional<ITypedIngredient<?>> getSharedIngredient(@Nullable Style style) {
		IJeiRuntime runtime = Internal.getOptionalJeiRuntime().orElse(null);
		if (!Objects.equals(sharedStyle, style) || sharedRuntime != runtime) {
			clearSharedIngredient();
			sharedStyle = style;
			sharedRuntime = runtime;
			var player = Minecraft.getInstance().player;
			if (runtime != null && player != null) {
				sharedIngredient = SharedChatIngredient.getSnapshot(style)
					.flatMap(snapshot -> SharedChatIngredient.decode(snapshot, player.registryAccess()))
					.flatMap(shared -> shared.resolve(runtime.getIngredientManager()));
			}
		}
		return sharedIngredient;
	}

	public record IngredientTooltipData<T>(
		ITypedIngredient<T> typedIngredient,
		IIngredientRenderer<T> ingredientRenderer,
		IIngredientManager ingredientManager,
		JeiTooltip tooltip
	) {
		public void draw(GuiGraphics guiGraphics, int mouseX, int mouseY) {
			tooltip.draw(guiGraphics, mouseX, mouseY, typedIngredient, ingredientRenderer, ingredientManager);
		}
	}

	public static boolean setTooltipForHoveredText(
		GuiGraphics guiGraphics,
		@Nullable Style hoveredStyle,
		int mouseX,
		int mouseY
	) {
		Optional<IngredientTooltipData<?>> optionalTooltipData = getTooltipForHoveredText(hoveredStyle);
		if (optionalTooltipData.isEmpty()) {
			return false;
		}

		IngredientTooltipData<?> tooltipData = optionalTooltipData.get();
		tooltipData.draw(guiGraphics, mouseX, mouseY);
		return true;
	}

	public static Optional<IngredientTooltipData<?>> getTooltipForHoveredChatLink(@Nullable Screen screen, double mouseX, double mouseY) {
		if (screen == null) {
			return Optional.empty();
		}
		return JeiChatItemLinkHover.getHoveredStyle(screen, mouseX, mouseY)
			.flatMap(ChatIngredientTooltip::getTooltipForHoveredText);
	}

	public static Optional<IngredientTooltipData<?>> getTooltipForHoveredText(@Nullable Style hoveredStyle) {
		Optional<ITypedIngredient<?>> shared = getSharedIngredient(hoveredStyle);
		if (shared.isPresent()) {
			return shared.map(ingredient -> createTooltipData(ingredient, Internal.getJeiRuntime().getIngredientManager()));
		}
		Optional<IngredientLink> optionalLink = JeiChatItemLinkHover.getIngredientLink(hoveredStyle);
		if (optionalLink.isEmpty()) {
			return Optional.empty();
		}

		Optional<IJeiRuntime> optionalRuntime = Internal.getOptionalJeiRuntime();
		if (optionalRuntime.isEmpty()) {
			return Optional.empty();
		}

		IJeiRuntime jeiRuntime = optionalRuntime.get();
		IIngredientManager ingredientManager = jeiRuntime.getIngredientManager();
		IngredientLink link = optionalLink.get();
		Optional<ITypedIngredient<?>> optionalTypedIngredient = JeiChatItemLinks.resolveTypedIngredient(link, ingredientManager);
		if (optionalTypedIngredient.isEmpty()) {
			return Optional.empty();
		}

		ITypedIngredient<?> typedIngredient = optionalTypedIngredient.get();
		IngredientTooltipData<?> tooltipData = createTooltipData(typedIngredient, ingredientManager);
		return Optional.of(tooltipData);
	}

	private static <T> IngredientTooltipData<T> createTooltipData(
		ITypedIngredient<T> typedIngredient,
		IIngredientManager ingredientManager
	) {
		IIngredientRenderer<T> ingredientRenderer = ingredientManager.getIngredientRenderer(typedIngredient.getType());
		JeiTooltip tooltip = new JeiTooltip();
		tooltip.add(new IngredientTooltipComponent<>(typedIngredient, ingredientRenderer));
		SafeIngredientUtil.getRichTooltip(tooltip, ingredientManager, ingredientRenderer, typedIngredient);
		return new IngredientTooltipData<>(typedIngredient, ingredientRenderer, ingredientManager, tooltip);
	}
}

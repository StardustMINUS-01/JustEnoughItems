package mezz.jei.gui.chat;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.common.chat.JeiChatItemLinkHover;
import mezz.jei.common.chat.JeiChatRecipeLinks;
import mezz.jei.common.chat.JeiChatRecipeLinks.RecipeLink;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.recipes.FocusedRecipeLayoutResolver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public final class ChatRecipeTooltip {
	private @Nullable Style style;
	private @Nullable IJeiRuntime runtime;
	private @Nullable IRecipeLayoutDrawable<?> layout;

	public void update(Screen screen, double mouseX, double mouseY) {
		Style hovered = JeiChatItemLinkHover.getHoveredStyle(screen, mouseX, mouseY).orElse(null);
		IJeiRuntime currentRuntime = Internal.getOptionalJeiRuntime().orElse(null);
		if (Objects.equals(style, hovered) && runtime == currentRuntime) {
			return;
		}
		clear();
		style = hovered;
		runtime = currentRuntime;
		ChatIngredientTooltip.getSharedIngredient(hovered);
		if (currentRuntime != null) {
			layout = JeiChatRecipeLinks.parse(hovered).flatMap(ChatRecipeTooltip::resolve).orElse(null);
		}
	}

	public static Optional<IRecipeLayoutDrawable<?>> resolve(RecipeLink recipe) {
		return Internal.getOptionalJeiRuntime().flatMap(runtime -> new FocusedRecipeLayoutResolver(runtime.getRecipeManager())
			.resolve(new FocusedRecipe(recipe.recipeType(), recipe.recipeId()), runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()));
	}

	public boolean draw(GuiGraphics graphics, int mouseX, int mouseY) {
		if (layout == null) {
			return false;
		}
		var rect = layout.getRect();
		var border = layout.getRectWithBorder();
		var position = DefaultTooltipPositioner.INSTANCE.positionTooltip(
			graphics.guiWidth(), graphics.guiHeight(), mouseX, mouseY, border.getWidth(), border.getHeight()
		);
		int x = Math.max(0, Math.min(position.x(), graphics.guiWidth() - border.getWidth()));
		int y = Math.max(0, Math.min(position.y(), graphics.guiHeight() - border.getHeight()));
		layout.setPosition(x + rect.getX() - border.getX(), y + rect.getY() - border.getY());
		var pose = graphics.pose();
		pose.pushPose();
		try {
			pose.translate(0, 0, 400);
			layout.drawRecipe(graphics, -1, -1);
		} finally {
			pose.popPose();
		}
		return true;
	}

	public void tick() {
		if (runtime != Internal.getOptionalJeiRuntime().orElse(null)) {
			clear();
		}
		if (layout != null) {
			layout.tick();
		}
	}

	public void clear() {
		ChatIngredientTooltip.clearSharedIngredient();
		style = null;
		runtime = null;
		layout = null;
	}
}

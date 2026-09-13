package mezz.jei.common.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.util.StringUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class JeiChatRecipeLinks {
	public static final int MAX_TITLE_LENGTH = 128;
	public static final int MAX_ID_LENGTH = 256;
	private static final String COMMAND = "jei_internal_recipe ";

	private JeiChatRecipeLinks() {
	}

	public record RecipeLink(ResourceLocation recipeType, ResourceLocation recipeId) {
	}

	public static Component create(RecipeLink recipe, String title) {
		String name = StringUtil.filterText(title).strip();
		return Component.literal("[" + name + "]").withStyle(style -> style
			.withColor(ChatFormatting.AQUA)
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(name)))
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, COMMAND + recipe.recipeType() + " " + recipe.recipeId()))
		);
	}

	public static Optional<RecipeLink> parse(@Nullable Style style) {
		if (style == null) {
			return Optional.empty();
		}
		ClickEvent click = style.getClickEvent();
		if (click == null || click.getAction() != ClickEvent.Action.RUN_COMMAND || !click.getValue().startsWith(COMMAND)) {
			return Optional.empty();
		}
		if (click.getValue().length() > COMMAND.length() + MAX_ID_LENGTH * 2 + 1) {
			return Optional.empty();
		}
		String[] ids = click.getValue().substring(COMMAND.length()).split(" ", -1);
		if (ids.length != 2 || ids[0].isEmpty() || ids[1].isEmpty() || ids[0].length() > MAX_ID_LENGTH || ids[1].length() > MAX_ID_LENGTH) {
			return Optional.empty();
		}
		ResourceLocation type = ResourceLocation.tryParse(ids[0]);
		ResourceLocation id = ResourceLocation.tryParse(ids[1]);
		if (type == null || id == null) {
			return Optional.empty();
		}
		return Optional.of(new RecipeLink(type, id));
	}
}

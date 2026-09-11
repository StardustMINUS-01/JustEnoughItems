package mezz.jei.gui.recipes;

import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.gui.HotkeyTooltipLine;
import mezz.jei.common.input.IInternalKeyMappings;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class RecipeButtonHotkeyTooltipUtil {
	private RecipeButtonHotkeyTooltipUtil() {
	}

	public static void addFavoriteButtonHotkeys(
		JeiTooltip tooltip,
		IInternalKeyMappings keyMappings,
		boolean canChangeResultIndex
	) {
		tooltip.add(CommonComponents.EMPTY);
		Component bookmarkKey = keyMappings.getBookmark().getTranslatedKeyMessage();
		HotkeyTooltipLine.add(
			tooltip,
			HotkeyTooltipLine.prefixed("SHIFT + ", bookmarkKey),
			"jei.tooltip.recipe.hotkeys.save_favorite_tree"
		);
		if (canChangeResultIndex) {
			HotkeyTooltipLine.add(
				tooltip,
				Component.translatable("jei.tooltip.recipe.keys.scroll"),
				"jei.tooltip.recipe.hotkeys.change_result_index"
			);
		}
	}
}

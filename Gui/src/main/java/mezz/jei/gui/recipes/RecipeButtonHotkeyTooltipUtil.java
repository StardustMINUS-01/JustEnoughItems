package mezz.jei.gui.recipes;

import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.gui.HotkeyTooltipLine;
import mezz.jei.common.input.IInternalKeyMappings;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class RecipeButtonHotkeyTooltipUtil {
	private RecipeButtonHotkeyTooltipUtil() {
	}

	public static void addOverlayButtonHotkeys(
		JeiTooltip tooltip,
		IInternalKeyMappings keyMappings,
		boolean canUseOverlayRenderer,
		boolean canFillCraftingGrid
	) {
		if (!canUseOverlayRenderer && !canFillCraftingGrid) {
			return;
		}

		tooltip.add(CommonComponents.EMPTY);
		if (canUseOverlayRenderer) {
			HotkeyTooltipLine.add(
				tooltip,
				keyMappings.getOverlayRecipe().getTranslatedKeyMessage(),
				"jei.tooltip.recipe.hotkeys.overlay_recipe"
			);
		}
		if (canFillCraftingGrid) {
			HotkeyTooltipLine.add(
				tooltip,
				HotkeyTooltipLine.prefixed("SHIFT + ", keyMappings.getOverlayRecipe().getTranslatedKeyMessage()),
				"jei.tooltip.recipe.hotkeys.fill_crafting_grid"
			);
		}
		addRecipeBookmarkHotkeys(tooltip, keyMappings);
	}

	public static void addBookmarkButtonHotkeys(
		JeiTooltip tooltip,
		IInternalKeyMappings keyMappings,
		boolean canChangeResultIndex
	) {
		tooltip.add(CommonComponents.EMPTY);
		addRecipeBookmarkHotkeys(tooltip, keyMappings);
		if (canChangeResultIndex) {
			HotkeyTooltipLine.add(
				tooltip,
				Component.translatable("jei.tooltip.recipe.keys.scroll"),
				"jei.tooltip.recipe.hotkeys.change_result_index"
			);
		}
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

	private static void addRecipeBookmarkHotkeys(JeiTooltip tooltip, IInternalKeyMappings keyMappings) {
		Component bookmarkKey = keyMappings.getBookmark().getTranslatedKeyMessage();
		HotkeyTooltipLine.add(
			tooltip,
			HotkeyTooltipLine.prefixed("SHIFT + ", bookmarkKey),
			"jei.tooltip.recipe.hotkeys.bookmark_recipe"
		);
		HotkeyTooltipLine.add(
			tooltip,
			HotkeyTooltipLine.prefixed("CTRL + SHIFT + ", bookmarkKey),
			"jei.tooltip.recipe.hotkeys.bookmark_recipe_and_count"
		);
	}
}

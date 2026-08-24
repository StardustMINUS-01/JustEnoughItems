package mezz.jei.common.gui;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.common.input.IInternalKeyMappings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class BookmarkHotkeyTooltipUtil {
	private BookmarkHotkeyTooltipUtil() {
	}

	public static void addIngredientHotkeys(ITooltipBuilder tooltip, IInternalKeyMappings keyBindings) {
		addIngredientHotkeys(tooltip, keyBindings, Screen.hasAltDown(), false, false, false);
	}

	public static void addIngredientHotkeys(
		ITooltipBuilder tooltip,
		IInternalKeyMappings keyBindings,
		boolean altDown,
		boolean canAutoCraft,
		boolean canEncodeAe2Pattern
	) {
		addIngredientHotkeys(tooltip, keyBindings, altDown, canAutoCraft, canEncodeAe2Pattern, false);
	}

	public static void addIngredientHotkeys(
		ITooltipBuilder tooltip,
		IInternalKeyMappings keyBindings,
		boolean altDown,
		boolean canAutoCraft,
		boolean canEncodeAe2Pattern,
		boolean showToggleInputCatalyst
	) {
		if (!addSeparatedAltHotkeySection(tooltip, altDown)) {
			return;
		}

		HotkeyTooltipLine.add(tooltip, keyBindings.getBookmark().getTranslatedKeyMessage(), "jei.tooltip.bookmarks.hotkeys.add");
		HotkeyTooltipLine.add(tooltip, keyBindings.getShowRecipe().getTranslatedKeyMessage(), "jei.tooltip.bookmarks.hotkeys.recipes");
		HotkeyTooltipLine.add(tooltip, keyBindings.getShowUses().getTranslatedKeyMessage(), "jei.tooltip.bookmarks.hotkeys.uses");
		HotkeyTooltipLine.add(tooltip, HotkeyTooltipLine.prefixed("CTRL + ", keyBindings.getBookmark().getTranslatedKeyMessage()), "jei.tooltip.bookmarks.hotkeys.add_with_count");
		HotkeyTooltipLine.add(tooltip, HotkeyTooltipLine.prefixed("SHIFT + ", keyBindings.getBookmark().getTranslatedKeyMessage()), "jei.tooltip.bookmarks.hotkeys.add_with_recipe");
		HotkeyTooltipLine.add(tooltip, HotkeyTooltipLine.prefixed("CTRL + SHIFT + ", keyBindings.getBookmark().getTranslatedKeyMessage()), "jei.tooltip.bookmarks.hotkeys.add_with_recipe_and_count");
		if (showToggleInputCatalyst) {
			HotkeyTooltipLine.add(
				tooltip,
				Component.translatable("jei.tooltip.bookmarks.group.keys.alt_scroll"),
				"jei.tooltip.bookmarks.group.hotkeys.toggle_input_catalyst"
			);
		}
		HotkeyTooltipLine.add(tooltip, keyBindings.getCopyIngredientName().getTranslatedKeyMessage(), "jei.tooltip.bookmarks.hotkeys.copy_name");
		HotkeyTooltipLine.add(tooltip, keyBindings.getCopyIngredientTags().getTranslatedKeyMessage(), "jei.tooltip.bookmarks.hotkeys.copy_tags");
		HotkeyTooltipLine.add(tooltip, keyBindings.getCopyIngredientId().getTranslatedKeyMessage(), "jei.tooltip.bookmarks.hotkeys.copy_id");
		HotkeyTooltipLine.add(tooltip, keyBindings.getCopyRecipeId().getTranslatedKeyMessage(), "jei.tooltip.bookmarks.hotkeys.copy_recipe_id");
		if (canEncodeAe2Pattern) {
			HotkeyTooltipLine.add(tooltip, keyBindings.getEncodeRecipeChainPatterns().getTranslatedKeyMessage(), "jei.tooltip.bookmarks.hotkeys.encode_ae2_pattern");
		}
		if (canAutoCraft) {
			Component favoriteRecipeKey = keyBindings.getFavoriteRecipe().getTranslatedKeyMessage();
			HotkeyTooltipLine.add(tooltip, favoriteRecipeKey, "jei.tooltip.bookmarks.hotkeys.favorite_recipe");
			HotkeyTooltipLine.add(tooltip, HotkeyTooltipLine.prefixed("SHIFT + ", favoriteRecipeKey), "jei.tooltip.recipe.hotkeys.save_favorite_tree");
			addCraftItemsHotkeys(tooltip, keyBindings, "jei.tooltip.bookmarks.hotkeys");
		}
	}

	public static void addGroupHotkeys(
		ITooltipBuilder tooltip,
		IInternalKeyMappings keyBindings,
		boolean altDown,
		boolean grouped,
		boolean craftingMode,
		boolean canEncodeAe2Patterns,
		boolean canBatchMarkAe2
	) {
		if (!craftingMode) {
			tooltip.add(Component.translatable("jei.tooltip.bookmarks.group").withStyle(ChatFormatting.GREEN));
		}
		if (!addAltHotkeySection(tooltip, altDown)) {
			return;
		}

		addPullHotkeys(tooltip, keyBindings);
		if (craftingMode) {
			addCraftItemsHotkeys(tooltip, keyBindings, "jei.tooltip.bookmarks.group.hotkeys");
		}
		if (canEncodeAe2Patterns) {
			HotkeyTooltipLine.add(tooltip, keyBindings.getEncodeRecipeChainPatterns().getTranslatedKeyMessage(), "jei.tooltip.bookmarks.group.hotkeys.encode_ae2_patterns");
		}
		if (grouped) {
			String rightClickAction = craftingMode ?
				"jei.tooltip.bookmarks.group.hotkeys.to_group" :
				"jei.tooltip.bookmarks.group.hotkeys.to_recipe_chain";
			HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.right"), rightClickAction);
			HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.left"), "jei.tooltip.bookmarks.group.hotkeys.mode");
			HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.right_drag"), "jei.tooltip.bookmarks.group.hotkeys.exclude");
			HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.left_drag"), "jei.tooltip.bookmarks.group.hotkeys.include");
			HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.alt_left"), "jei.tooltip.bookmarks.group.hotkeys.collapse");
			HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.ctrl_scroll"), "jei.tooltip.bookmarks.group.hotkeys.quantity");
			HotkeyTooltipLine.add(tooltip, HotkeyTooltipLine.prefixed("SHIFT + ", keyBindings.getBookmark().getTranslatedKeyMessage()), "jei.tooltip.bookmarks.group.hotkeys.remove");
			HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.ctrl_alt_scroll"), "jei.tooltip.bookmarks.group.hotkeys.quantity_step");
			HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.shift_left_drag"), "jei.tooltip.bookmarks.group.hotkeys.sort");
			if (canBatchMarkAe2) {
				HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.ctrl_left_drag"), "jei.tooltip.bookmarks.group.hotkeys.batch_mark_ae2");
			}
		} else {
			HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.left_drag"), "jei.tooltip.bookmarks.group.hotkeys.include");
		}
	}

	public static void addDefaultGroupControlHotkeys(ITooltipBuilder tooltip, IInternalKeyMappings keyBindings, boolean altDown) {
		addDefaultGroupControlHotkeys(tooltip, keyBindings, altDown, false, true);
	}

	public static void addDefaultGroupControlHotkeys(
		ITooltipBuilder tooltip,
		IInternalKeyMappings keyBindings,
		boolean altDown,
		boolean craftingMode,
		boolean canPullItems
	) {
		if (!craftingMode) {
			tooltip.add(Component.translatable("jei.tooltip.bookmarks.default_group").withStyle(ChatFormatting.GREEN));
		}
		if (!addAltHotkeySection(tooltip, altDown)) {
			return;
		}

		if (canPullItems) {
			addPullHotkeys(tooltip, keyBindings);
		}
		String rightClickAction = craftingMode ?
			"jei.tooltip.bookmarks.group.hotkeys.to_group" :
			"jei.tooltip.bookmarks.group.hotkeys.to_recipe_chain";
		HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.right"), rightClickAction);
		HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.left"), "jei.tooltip.bookmarks.group.hotkeys.mode");
		HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.alt_left"), "jei.tooltip.bookmarks.group.hotkeys.collapse");
		HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.ctrl_scroll"), "jei.tooltip.bookmarks.group.hotkeys.quantity");
		HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.ctrl_alt_scroll"), "jei.tooltip.bookmarks.group.hotkeys.quantity_step");
	}

	public static void addFavoriteRecipeHotkeys(ITooltipBuilder tooltip, IInternalKeyMappings keyBindings) {
		if (!addSeparatedAltHotkeySection(tooltip, Screen.hasAltDown())) {
			return;
		}

		Component bookmarkKey = keyBindings.getBookmark().getTranslatedKeyMessage();
		HotkeyTooltipLine.add(tooltip, bookmarkKey, "jei.tooltip.favoriteRecipes.remove");
		HotkeyTooltipLine.add(tooltip, HotkeyTooltipLine.prefixed("SHIFT + ", bookmarkKey), "jei.tooltip.favoriteRecipes.saveTree");
	}

	public static void addFavoriteRecipeRowHotkeys(ITooltipBuilder tooltip) {
		tooltip.add(Component.translatable("jei.tooltip.favoriteRecipes.recipeRow").withStyle(ChatFormatting.GREEN));
		if (!addAltHotkeySection(tooltip, Screen.hasAltDown())) {
			return;
		}

		HotkeyTooltipLine.add(tooltip, Component.translatable("jei.tooltip.bookmarks.group.keys.left"), "jei.tooltip.favoriteRecipes.rowToggle");
	}

	private static boolean addSeparatedAltHotkeySection(ITooltipBuilder tooltip, boolean altDown) {
		tooltip.add(CommonComponents.EMPTY);
		return addAltHotkeySection(tooltip, altDown);
	}

	private static boolean addAltHotkeySection(ITooltipBuilder tooltip, boolean altDown) {
		if (!altDown) {
			addHoldAltPrompt(tooltip);
			return false;
		}
		return true;
	}

	private static void addPullHotkeys(ITooltipBuilder tooltip, IInternalKeyMappings keyBindings) {
		Component pullKey = keyBindings.getBookmarkPullItems().getTranslatedKeyMessage();
		HotkeyTooltipLine.add(tooltip, pullKey, "jei.tooltip.bookmarks.group.hotkeys.pull_items");
		HotkeyTooltipLine.add(tooltip, HotkeyTooltipLine.prefixed("SHIFT + ", pullKey), "jei.tooltip.bookmarks.group.hotkeys.pull_items_shift");
	}

	private static void addCraftItemsHotkeys(ITooltipBuilder tooltip, IInternalKeyMappings keyBindings, String translationPrefix) {
		Component craftKey = keyBindings.getCraftItems().getTranslatedKeyMessage();
		HotkeyTooltipLine.add(tooltip, HotkeyTooltipLine.prefixed("CTRL + SHIFT + ", craftKey), translationPrefix + ".craft_missing");
		HotkeyTooltipLine.add(tooltip, HotkeyTooltipLine.prefixed("SHIFT + ", craftKey), translationPrefix + ".craft_items");
	}

	private static void addHoldAltPrompt(ITooltipBuilder tooltip) {
		tooltip.add(
			Component.translatable("jei.tooltip.bookmarks.hotkeys.hold_alt.prefix").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("ALT").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW))
				.append(Component.translatable("jei.tooltip.bookmarks.hotkeys.hold_alt.suffix").withStyle(ChatFormatting.GRAY))
		);
	}

}

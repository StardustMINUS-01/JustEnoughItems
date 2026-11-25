package mezz.jei.gui.overlay.ingredients;

import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.common.gui.BookmarkHotkeyTooltipUtil.IngredientAction;
import mezz.jei.common.gui.BookmarkHotkeyTooltipUtil;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.SafeIngredientUtil;
import mezz.jei.gui.compat.ExternalIngredientSearchHandlerRegistry;
import mezz.jei.common.config.SearchMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class IngredientGridTooltipHelper {
	private final IIngredientManager ingredientManager;
	private final IIngredientFilterConfig ingredientFilterConfig;
	private final IClientToggleState toggleState;
	private final IInternalKeyMappings keyBindings;
	private final IColorHelper colorHelper;

	private final boolean bookmarkGrid;

	public IngredientGridTooltipHelper(
		IIngredientManager ingredientManager,
		IIngredientFilterConfig ingredientFilterConfig,
		IClientToggleState toggleState,
		IInternalKeyMappings keyBindings,
		IColorHelper colorHelper
	) {
		this(ingredientManager, ingredientFilterConfig, toggleState, keyBindings, colorHelper, false);
	}

	IngredientGridTooltipHelper(
		IIngredientManager ingredientManager,
		IIngredientFilterConfig ingredientFilterConfig,
		IClientToggleState toggleState,
		IInternalKeyMappings keyBindings,
		IColorHelper colorHelper,
		boolean bookmarkGrid
	) {
		this.bookmarkGrid = bookmarkGrid;
		this.ingredientManager = ingredientManager;
		this.ingredientFilterConfig = ingredientFilterConfig;
		this.toggleState = toggleState;
		this.keyBindings = keyBindings;
		this.colorHelper = colorHelper;
	}

	public <T> void getIngredientTooltip(
		JeiTooltip tooltip,
		ITypedIngredient<T> typedIngredient,
		IIngredientRenderer<T> ingredientRenderer,
		IIngredientHelper<T> ingredientHelper
	) {
		getIngredientTooltip(tooltip, typedIngredient, ingredientRenderer, ingredientHelper, true);
	}

	public <T> void getIngredientTooltip(
		JeiTooltip tooltip,
		ITypedIngredient<T> typedIngredient,
		IIngredientRenderer<T> ingredientRenderer,
		IIngredientHelper<T> ingredientHelper,
		boolean includeHotkeys
	) {
		getIngredientTooltip(tooltip, typedIngredient, ingredientRenderer, ingredientHelper, includeHotkeys, false);
	}

	public <T> void getIngredientTooltip(
		JeiTooltip tooltip,
		ITypedIngredient<T> typedIngredient,
		IIngredientRenderer<T> ingredientRenderer,
		IIngredientHelper<T> ingredientHelper,
		boolean includeHotkeys,
		boolean showToggleInputCatalyst
	) {
		SafeIngredientUtil.getTooltip(tooltip, ingredientManager, ingredientRenderer, typedIngredient);

		if (ingredientFilterConfig.colorSearchMode().get() != SearchMode.DISABLED) {
			addColorSearchInfoToTooltip(tooltip, typedIngredient, ingredientHelper);
		}

		if (ingredientFilterConfig.searchIngredientAliases().get()) {
			addIngredientAliasesToTooltip(tooltip, typedIngredient, ingredientManager);
		}

		if (toggleState.isEditModeEnabled()) {
			addEditModeInfoToTooltip(tooltip, keyBindings);
		}

		if (includeHotkeys) {
			boolean controlDown = Screen.hasControlDown();
			Screen screen = Minecraft.getInstance().screen;
			var actions = EnumSet.noneOf(IngredientAction.class);
			if (bookmarkGrid)
				actions.add(IngredientAction.REMOVE_BOOKMARK);
			if (typedIngredient.getItemStack().isPresent())
				actions.add(IngredientAction.COPY_COMPONENTS);
			if (showToggleInputCatalyst)
				actions.add(IngredientAction.TOGGLE_CATALYST);
			if (controlDown && screen != null && ExternalIngredientSearchHandlerRegistry.search(screen, typedIngredient, true)) {
				actions.add(IngredientAction.SEARCH_TERMINAL);
			}
			BookmarkHotkeyTooltipUtil.addIngredientHotkeys(tooltip, keyBindings, Screen.hasAltDown(), controlDown, actions);
		}
	}

	private <T> void addIngredientAliasesToTooltip(JeiTooltip tooltip, ITypedIngredient<T> typedIngredient, IIngredientManager ingredientManager) {
		Collection<String> aliases = ingredientManager.getIngredientAliases(typedIngredient);
		if (aliases.isEmpty()) {
			return;
		}
		tooltip.add(Component.empty());
		tooltip.add(
			Component.translatable("jei.tooltip.item.search.aliases")
				.withStyle(ChatFormatting.GRAY)
		);
		for (String alias : aliases) {
			tooltip.add(
				Component.literal("• " + alias)
					.withStyle(ChatFormatting.GRAY)
			);
		}
	}

	private <T> void addColorSearchInfoToTooltip(JeiTooltip tooltip, ITypedIngredient<T> typedIngredient, IIngredientHelper<T> ingredientHelper) {
		Iterable<Integer> colors = ingredientHelper.getColors(typedIngredient.getIngredient());
		String colorNamesString = StreamSupport.stream(colors.spliterator(), false)
			.map(colorHelper::getClosestColorName)
			.collect(Collectors.joining(", "));
		if (!colorNamesString.isEmpty()) {
			Component colorTranslation = Component.translatable("jei.tooltip.item.colors", colorNamesString)
				.withStyle(ChatFormatting.GRAY);
			tooltip.add(colorTranslation);
		}
	}

	private static void addEditModeInfoToTooltip(JeiTooltip tooltip, IInternalKeyMappings keyBindings) {
		tooltip.add(CommonComponents.EMPTY);
		tooltip.add(
			Component.translatable("gui.jei.editMode.description")
				.withStyle(ChatFormatting.DARK_GREEN)
		);
		tooltip.addKeyUsageComponent(
			"gui.jei.editMode.description.hide",
			keyBindings.getToggleHideIngredient()
		);
		tooltip.addKeyUsageComponent(
			"gui.jei.editMode.description.hide.wild",
			keyBindings.getToggleWildcardHideIngredient()
		);
	}
}

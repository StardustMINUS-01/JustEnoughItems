package mezz.jei.test.common.gui;

import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.gui.BookmarkHotkeyTooltipUtil.IngredientAction;
import mezz.jei.common.gui.BookmarkHotkeyTooltipUtil;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.util.Set;

public class BookmarkHotkeyTooltipUtilTest {
	@Test
	public void ingredientTooltipShowsShareHotkey() {
		JeiTooltip tooltip = new JeiTooltip();

		BookmarkHotkeyTooltipUtil.addIngredientHotkeys(tooltip, createKeyMappings(), false, true, Set.of());

		Assertions.assertTrue(tooltip.toString().contains("jei.tooltip.bookmarks.hotkeys.share"));
	}

	@Test
	public void ingredientTooltipShowsTerminalSearchOnlyWhenAvailable() {
		JeiTooltip availableTooltip = new JeiTooltip();
		JeiTooltip unavailableTooltip = new JeiTooltip();

		BookmarkHotkeyTooltipUtil.addIngredientHotkeys(availableTooltip, createKeyMappings(), false, true, Set.of(IngredientAction.SEARCH_TERMINAL));
		BookmarkHotkeyTooltipUtil.addIngredientHotkeys(unavailableTooltip, createKeyMappings(), false, true, Set.of());

		Assertions.assertTrue(availableTooltip.toString().contains("jei.tooltip.bookmarks.hotkeys.search_terminal"));
		Assertions.assertFalse(unavailableTooltip.toString().contains("jei.tooltip.bookmarks.hotkeys.search_terminal"));
	}

	@Test
	public void defaultGroupTooltipDescribesUngroupedBookmarksAndHidesUnavailablePullActions() {
		JeiTooltip tooltip = new JeiTooltip();

		BookmarkHotkeyTooltipUtil.addDefaultGroupControlHotkeys(tooltip, null, true, false, false, false, false, false, false);

		String text = tooltip.toString();
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.default_group"));
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.group.hotkeys.to_recipe_chain"));
		Assertions.assertFalse(text.contains("jei.tooltip.bookmarks.group.hotkeys.pull_items"));
	}

	@Test
	public void defaultRecipeChainTooltipShowsGroupConversionAndAvailablePullActions() {
		JeiTooltip tooltip = new JeiTooltip();

		BookmarkHotkeyTooltipUtil.addDefaultGroupControlHotkeys(tooltip, createKeyMappings(), true, false, true, false, false, true, false);

		String text = tooltip.toString();
		Assertions.assertFalse(text.contains("jei.tooltip.bookmarks.default_group"));
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.group.hotkeys.to_group"));
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.group.hotkeys.pull_items"));
		Assertions.assertTrue(text.contains("V"));
	}

	@ParameterizedTest
	@CsvSource(textBlock = """
		# alt, ctrl, chain, canEncode, canCraft, showEncode, showCraft
		true,  true,  true,  false, false, false, false
		true,  true,  false, true,  true,  true,  false
		true,  true,  true,  false, true,  false, true
		true,  true,  true,  true,  false, true,  false
		false, true,  true,  true,  true,  true,  false
		true,  false, true,  true,  true,  false, true
		true,  true,  true,  true,  true,  true,  true
		""")
	public void defaultGroupShowsAvailableEncodingAndCraftingHotkeys(
		boolean alt, boolean control, boolean chain, boolean canEncode, boolean canCraft, boolean showEncode, boolean showCraft
	) {
		JeiTooltip tooltip = new JeiTooltip();
		BookmarkHotkeyTooltipUtil.addDefaultGroupControlHotkeys(tooltip, createKeyMappings(), alt, control, chain, canEncode, false, false, canCraft);
		String text = tooltip.toString();
		Assertions.assertEquals(showEncode, text.contains("hotkeys.encode_ae2_patterns"));
		Assertions.assertEquals(showCraft, text.contains("hotkeys.craft_missing"));
		Assertions.assertEquals(showCraft, text.contains("hotkeys.craft_items"));
	}

	@Test
	public void controlAndAltSectionsExpandIndependently() {
		for (boolean alt : new boolean[]{false, true}) {
			for (boolean control : new boolean[]{false, true}) {
				JeiTooltip tooltip = new JeiTooltip();
				BookmarkHotkeyTooltipUtil.addIngredientHotkeys(tooltip, createKeyMappings(), alt, control, Set.of(IngredientAction.FAVORITE_RECIPE, IngredientAction.CRAFT, IngredientAction.ENCODE_PATTERN, IngredientAction.SEARCH_TERMINAL));
				String text = tooltip.toString();
				Assertions.assertEquals(control, text.contains("hotkeys.copy_name"));
				Assertions.assertEquals(control, text.contains("hotkeys.encode_ae2_pattern"));
				Assertions.assertEquals(control, text.contains("hotkeys.add_with_count"));
				Assertions.assertEquals(alt, text.contains("hotkeys.add_with_recipe_and_count"));
				Assertions.assertEquals(alt, text.contains("hotkeys.craft_missing"));
				Assertions.assertEquals(!control, text.contains("hotkeys.hold_ctrl"));
			}
		}
	}

	@Test
	public void outputMayOfferFavoriteWithoutCrafting() {
		JeiTooltip tooltip = new JeiTooltip();
		BookmarkHotkeyTooltipUtil.addIngredientHotkeys(tooltip, createKeyMappings(), true, true,
			Set.of(IngredientAction.COPY_COMPONENTS, IngredientAction.FAVORITE_RECIPE));
		String text = tooltip.toString();
		Assertions.assertTrue(text.contains("hotkeys.favorite_recipe"));
		Assertions.assertTrue(text.contains("hotkeys.copy_components"));
		Assertions.assertFalse(text.contains("hotkeys.craft_missing"));
		Assertions.assertFalse(text.contains("hotkeys.encode_ae2_pattern"));
	}

	@Test
	public void groupControlActionsAndShiftTreeEntryHaveSeparateSections() {
		JeiTooltip control = new JeiTooltip();
		JeiTooltip alt = new JeiTooltip();
		BookmarkHotkeyTooltipUtil.addGroupHotkeys(control, createKeyMappings(), false, true, true, true, true, true, true, false, false);
		BookmarkHotkeyTooltipUtil.addGroupHotkeys(alt, createKeyMappings(), true, false, true, true, true, true, true, false, false);
		Assertions.assertTrue(control.toString().contains("hotkeys.encode_ae2_patterns"));
		Assertions.assertFalse(alt.toString().contains("hotkeys.encode_ae2_patterns"));
		Assertions.assertTrue(alt.toString().contains("jei.tree.open"));
		Assertions.assertFalse(control.toString().contains("jei.tree.open"));
		Assertions.assertTrue(alt.toString().contains("hotkeys.quantity_step"));
		Assertions.assertFalse(alt.toString().contains("hotkeys.pull_items"));
		Assertions.assertFalse(alt.toString().contains("hotkeys.craft_missing"));
		JeiTooltip ordinary = new JeiTooltip();
		BookmarkHotkeyTooltipUtil.addGroupHotkeys(ordinary, createKeyMappings(), true, false, true, false, false, false, false, false, false);
		Assertions.assertFalse(ordinary.toString().contains("jei.tree.open"));
	}

	@Test
	public void bookmarkTooltipDescribesRemovalAndOmitsItemOnlyCopyForFluidBookmarks() {
		JeiTooltip tooltip = new JeiTooltip();
		BookmarkHotkeyTooltipUtil.addIngredientHotkeys(tooltip, createKeyMappings(), true, true, Set.of(IngredientAction.REMOVE_BOOKMARK));
		String text = tooltip.toString();
		Assertions.assertTrue(text.contains("hotkeys.remove"));
		Assertions.assertFalse(text.contains("hotkeys.add"));
		Assertions.assertFalse(text.contains("hotkeys.copy_components"));
	}

	private static IInternalKeyMappings createKeyMappings() {
		IJeiKeyMapping key = (IJeiKeyMapping) Proxy.newProxyInstance(
			IJeiKeyMapping.class.getClassLoader(),
			new Class<?>[]{mezz.jei.common.input.keys.IJeiKeyMappingWithExtraModifiers.class},
			(proxy, method, args) -> method.getName().equals("getTranslatedKeyMessage") ? Component.literal("V") : false
		);
		return (IInternalKeyMappings) Proxy.newProxyInstance(
			IInternalKeyMappings.class.getClassLoader(),
			new Class<?>[]{IInternalKeyMappings.class},
			(proxy, method, args) -> IJeiKeyMapping.class.isAssignableFrom(method.getReturnType()) ? key : null
		);
	}
}

package mezz.jei.test.common.gui;

import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.gui.BookmarkHotkeyTooltipUtil;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

public class BookmarkHotkeyTooltipUtilTest {
	@Test
	public void ingredientTooltipShowsShareHotkey() {
		JeiTooltip tooltip = new JeiTooltip();

		BookmarkHotkeyTooltipUtil.addIngredientHotkeys(tooltip, createKeyMappings(), false, true, false, false, false, false);

		Assertions.assertTrue(tooltip.toString().contains("jei.tooltip.bookmarks.hotkeys.share"));
	}

	@Test
	public void ingredientTooltipShowsTerminalSearchOnlyWhenAvailable() {
		JeiTooltip availableTooltip = new JeiTooltip();
		JeiTooltip unavailableTooltip = new JeiTooltip();

		BookmarkHotkeyTooltipUtil.addIngredientHotkeys(availableTooltip, createKeyMappings(), false, true, false, false, false, true);
		BookmarkHotkeyTooltipUtil.addIngredientHotkeys(unavailableTooltip, createKeyMappings(), false, true, false, false, false, false);

		Assertions.assertTrue(availableTooltip.toString().contains("jei.tooltip.bookmarks.hotkeys.search_terminal"));
		Assertions.assertFalse(unavailableTooltip.toString().contains("jei.tooltip.bookmarks.hotkeys.search_terminal"));
	}

	@Test
	public void defaultGroupTooltipDescribesUngroupedBookmarksAndHidesUnavailablePullActions() {
		JeiTooltip tooltip = new JeiTooltip();

		BookmarkHotkeyTooltipUtil.addDefaultGroupControlHotkeys(tooltip, null, true, false, false, false, false);

		String text = tooltip.toString();
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.default_group"));
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.group.hotkeys.to_recipe_chain"));
		Assertions.assertFalse(text.contains("jei.tooltip.bookmarks.group.hotkeys.pull_items"));
	}

	@Test
	public void defaultRecipeChainTooltipShowsGroupConversionAndAvailablePullActions() {
		JeiTooltip tooltip = new JeiTooltip();

		BookmarkHotkeyTooltipUtil.addDefaultGroupControlHotkeys(tooltip, createKeyMappings(), true, false, true, true, false);

		String text = tooltip.toString();
		Assertions.assertFalse(text.contains("jei.tooltip.bookmarks.default_group"));
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.group.hotkeys.to_group"));
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.group.hotkeys.pull_items"));
		Assertions.assertTrue(text.contains("V"));
	}

	@Test
	public void controlAndAltSectionsExpandIndependently() {
		for (boolean alt : new boolean[]{false, true}) {
			for (boolean control : new boolean[]{false, true}) {
				JeiTooltip tooltip = new JeiTooltip();
				BookmarkHotkeyTooltipUtil.addIngredientHotkeys(tooltip, createKeyMappings(), alt, control, true, true, false, true);
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
	public void groupControlActionsAndShiftTreeEntryHaveSeparateSections() {
		JeiTooltip control = new JeiTooltip();
		JeiTooltip alt = new JeiTooltip();
		BookmarkHotkeyTooltipUtil.addGroupHotkeys(control, createKeyMappings(), false, true, true, true, true, true, true);
		BookmarkHotkeyTooltipUtil.addGroupHotkeys(alt, createKeyMappings(), true, false, true, true, true, true, true);
		Assertions.assertTrue(control.toString().contains("hotkeys.encode_ae2_patterns"));
		Assertions.assertFalse(alt.toString().contains("hotkeys.encode_ae2_patterns"));
		Assertions.assertTrue(alt.toString().contains("jei.tree.open"));
		Assertions.assertFalse(control.toString().contains("jei.tree.open"));
		Assertions.assertTrue(alt.toString().contains("hotkeys.quantity_step"));
		JeiTooltip ordinary = new JeiTooltip();
		BookmarkHotkeyTooltipUtil.addGroupHotkeys(ordinary, createKeyMappings(), true, false, true, false, false, false, false);
		Assertions.assertFalse(ordinary.toString().contains("jei.tree.open"));
	}

	private static IInternalKeyMappings createKeyMappings() {
		IJeiKeyMapping key = (IJeiKeyMapping) Proxy.newProxyInstance(
			IJeiKeyMapping.class.getClassLoader(),
			new Class<?>[]{IJeiKeyMapping.class},
			(proxy, method, args) -> method.getName().equals("getTranslatedKeyMessage") ? Component.literal("V") : false
		);
		return (IInternalKeyMappings) Proxy.newProxyInstance(
			IInternalKeyMappings.class.getClassLoader(),
			new Class<?>[]{IInternalKeyMappings.class},
			(proxy, method, args) -> IJeiKeyMapping.class.isAssignableFrom(method.getReturnType()) ? key : null
		);
	}
}

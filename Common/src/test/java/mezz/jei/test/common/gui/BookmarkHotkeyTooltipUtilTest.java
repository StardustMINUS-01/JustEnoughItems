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

		BookmarkHotkeyTooltipUtil.addIngredientHotkeys(tooltip, createKeyMappings(), true, false, false, false);

		Assertions.assertTrue(tooltip.toString().contains("jei.tooltip.bookmarks.hotkeys.share"));
	}

	@Test
	public void ingredientTooltipShowsTerminalSearchOnlyWhenAvailable() {
		JeiTooltip availableTooltip = new JeiTooltip();
		JeiTooltip unavailableTooltip = new JeiTooltip();

		BookmarkHotkeyTooltipUtil.addIngredientHotkeys(availableTooltip, createKeyMappings(), true, false, false, false, true);
		BookmarkHotkeyTooltipUtil.addIngredientHotkeys(unavailableTooltip, createKeyMappings(), true, false, false, false, false);

		Assertions.assertTrue(availableTooltip.toString().contains("jei.tooltip.bookmarks.hotkeys.search_terminal"));
		Assertions.assertFalse(unavailableTooltip.toString().contains("jei.tooltip.bookmarks.hotkeys.search_terminal"));
	}

	@Test
	public void defaultGroupTooltipDescribesUngroupedBookmarksAndHidesUnavailablePullActions() {
		JeiTooltip tooltip = new JeiTooltip();

		BookmarkHotkeyTooltipUtil.addDefaultGroupControlHotkeys(tooltip, null, true, false, false, false);

		String text = tooltip.toString();
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.default_group"));
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.group.hotkeys.to_recipe_chain"));
		Assertions.assertFalse(text.contains("jei.tooltip.bookmarks.group.hotkeys.pull_items"));
	}

	@Test
	public void defaultRecipeChainTooltipShowsGroupConversionAndAvailablePullActions() {
		JeiTooltip tooltip = new JeiTooltip();

		BookmarkHotkeyTooltipUtil.addDefaultGroupControlHotkeys(tooltip, createKeyMappings(), true, true, true, false);

		String text = tooltip.toString();
		Assertions.assertFalse(text.contains("jei.tooltip.bookmarks.default_group"));
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.group.hotkeys.to_group"));
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.group.hotkeys.pull_items"));
		Assertions.assertTrue(text.contains("V"));
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

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
	public void defaultGroupTooltipDescribesUngroupedBookmarksAndHidesUnavailablePullActions() {
		JeiTooltip tooltip = new JeiTooltip();

		BookmarkHotkeyTooltipUtil.addDefaultGroupControlHotkeys(tooltip, null, true, false, false);

		String text = tooltip.toString();
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.default_group"));
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.group.hotkeys.to_recipe_chain"));
		Assertions.assertFalse(text.contains("jei.tooltip.bookmarks.group.hotkeys.pull_items"));
	}

	@Test
	public void defaultRecipeChainTooltipShowsGroupConversionAndAvailablePullActions() {
		JeiTooltip tooltip = new JeiTooltip();

		BookmarkHotkeyTooltipUtil.addDefaultGroupControlHotkeys(tooltip, createKeyMappings(), true, true, true);

		String text = tooltip.toString();
		Assertions.assertFalse(text.contains("jei.tooltip.bookmarks.default_group"));
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.group.hotkeys.to_group"));
		Assertions.assertTrue(text.contains("jei.tooltip.bookmarks.group.hotkeys.pull_items"));
		Assertions.assertTrue(text.contains("V"));
	}

	private static IInternalKeyMappings createKeyMappings() {
		IJeiKeyMapping pullKey = (IJeiKeyMapping) Proxy.newProxyInstance(
			IJeiKeyMapping.class.getClassLoader(),
			new Class<?>[]{IJeiKeyMapping.class},
			(proxy, method, args) -> method.getName().equals("getTranslatedKeyMessage") ? Component.literal("V") : false
		);
		return (IInternalKeyMappings) Proxy.newProxyInstance(
			IInternalKeyMappings.class.getClassLoader(),
			new Class<?>[]{IInternalKeyMappings.class},
			(proxy, method, args) -> method.getName().equals("getBookmarkPullItems") ? pullKey : null
		);
	}
}

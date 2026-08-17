package mezz.jei.common.gui;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class HotkeyTooltipLine {
	private HotkeyTooltipLine() {
	}

	public static MutableComponent prefixed(String prefix, Component key) {
		return Component.literal(prefix).append(key);
	}

	public static void add(ITooltipBuilder tooltip, Component key, String translationKey) {
		tooltip.add(
			Component.literal("")
				.append(key.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW))
				.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
				.append(Component.translatable(translationKey).withStyle(ChatFormatting.GRAY))
		);
	}
}

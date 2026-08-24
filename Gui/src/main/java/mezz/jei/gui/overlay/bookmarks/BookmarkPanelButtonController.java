package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.input.IInternalKeyMappings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

class BookmarkPanelButtonController implements IIconButtonController {
	private final IDrawable offIcon;
	private final IDrawable onIcon;
	private final BookmarkOverlay bookmarkOverlay;
	private final IInternalKeyMappings keyBindings;

	BookmarkPanelButtonController(BookmarkOverlay bookmarkOverlay, IInternalKeyMappings keyBindings) {
		Textures textures = Internal.getTextures();
		this.offIcon = textures.getBookmarkButtonDisabledIcon();
		this.onIcon = textures.getBookmarkButtonEnabledIcon();
		this.bookmarkOverlay = bookmarkOverlay;
		this.keyBindings = keyBindings;
	}

	@Override
	public void getTooltips(ITooltipBuilder tooltip) {
		tooltip.add(Component.translatable("jei.tooltip.bookmarks"));
		IJeiKeyMapping bookmarkKey = keyBindings.getBookmark();
		if (bookmarkKey.isUnbound()) {
			MutableComponent noKey = Component.translatable("jei.tooltip.bookmarks.usage.nokey");
			tooltip.add(noKey.withStyle(ChatFormatting.RED));
		} else if (!bookmarkOverlay.hasBookmarkPanelRoom()) {
			MutableComponent notEnoughSpace = Component.translatable("jei.tooltip.bookmarks.not.enough.space");
			tooltip.add(notEnoughSpace.withStyle(ChatFormatting.GOLD));
		} else {
			tooltip.addKeyUsageComponent(
				"jei.tooltip.bookmarks.usage.key",
				bookmarkKey
			);
		}
	}

	@Override
	public void updateState(IButtonState state) {
		boolean iconToggledOn = bookmarkOverlay.isListDisplayed();
		state.setIcon(iconToggledOn ? onIcon : offIcon);
		state.setForcePressed(iconToggledOn);
	}

	@Override
	public boolean onPress(IJeiUserInput input) {
		return bookmarkOverlay.toggleBookmarkPanel(input.isSimulate());
	}
}

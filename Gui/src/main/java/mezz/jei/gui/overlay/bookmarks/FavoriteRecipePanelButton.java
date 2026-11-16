package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.common.Internal;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.InputModifiers;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class FavoriteRecipePanelButton implements mezz.jei.api.gui.buttons.IIconButtonController {
	public static IconButton create(BookmarkOverlay bookmarkOverlay, FavoriteRecipeStore favoriteRecipes) {
		Textures textures = Internal.getTextures();
		IDrawableStatic offIcon = textures.getFavoriteButtonDisabledIcon();
		IDrawableStatic onIcon = textures.getFavoriteButtonEnabledIcon();
		return new IconButton(new FavoriteRecipePanelButton(offIcon, onIcon, bookmarkOverlay, favoriteRecipes));
	}

	private final IDrawable offIcon;
	private final IDrawable onIcon;
	private final BookmarkOverlay bookmarkOverlay;
	private final FavoriteRecipeStore favoriteRecipes;

	private FavoriteRecipePanelButton(
		IDrawable offIcon,
		IDrawable onIcon,
		BookmarkOverlay bookmarkOverlay,
		FavoriteRecipeStore favoriteRecipes
	) {
		this.offIcon = offIcon;
		this.onIcon = onIcon;
		this.bookmarkOverlay = bookmarkOverlay;
		this.favoriteRecipes = favoriteRecipes;
	}

	@Override
	public void getTooltips(ITooltipBuilder tooltip) {
		if (bookmarkOverlay.isFavoritePanelDisplayed()) {
			tooltip.add(Component.translatable("jei.tooltip.favoriteRecipes.disable"));
		} else {
			tooltip.add(Component.translatable("jei.tooltip.favoriteRecipes.enable"));
		}
		if (favoriteRecipes.isEmpty()) {
			tooltip.add(Component.translatable("jei.tooltip.favoriteRecipes.empty").withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable("jei.tooltip.favoriteRecipes.changeDisplayMode").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public void updateState(mezz.jei.api.gui.buttons.IButtonState state) {
		state.setIcon(bookmarkOverlay.isFavoritePanelDisplayed() ? onIcon : offIcon);
	}

	@Override
	public boolean onPress(mezz.jei.api.gui.inputs.IJeiUserInput input) {
		if (Screen.hasShiftDown() || InputModifiers.hasShift(input.getModifiers())) {
			return bookmarkOverlay.cycleFavoritePanelDisplayMode(input.isSimulate());
		}
		return bookmarkOverlay.toggleFavoritePanel(input.isSimulate());
	}
}

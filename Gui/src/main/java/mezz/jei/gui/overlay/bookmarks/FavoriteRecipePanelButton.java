package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.gui.elements.GuiIconToggleButton;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.UserInput;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class FavoriteRecipePanelButton extends GuiIconToggleButton {
	public static FavoriteRecipePanelButton create(BookmarkOverlay bookmarkOverlay, FavoriteRecipeStore favoriteRecipes) {
		Textures textures = Internal.getTextures();
		IDrawableStatic offIcon = textures.getFavoriteButtonDisabledIcon();
		IDrawableStatic onIcon = textures.getFavoriteButtonEnabledIcon();
		return new FavoriteRecipePanelButton(offIcon, onIcon, bookmarkOverlay, favoriteRecipes);
	}

	private final BookmarkOverlay bookmarkOverlay;
	private final FavoriteRecipeStore favoriteRecipes;

	private FavoriteRecipePanelButton(
		IDrawable offIcon,
		IDrawable onIcon,
		BookmarkOverlay bookmarkOverlay,
		FavoriteRecipeStore favoriteRecipes
	) {
		super(offIcon, onIcon);
		this.bookmarkOverlay = bookmarkOverlay;
		this.favoriteRecipes = favoriteRecipes;
	}

	@Override
	protected void getTooltips(JeiTooltip tooltip) {
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
	protected boolean isIconToggledOn() {
		return bookmarkOverlay.isFavoritePanelDisplayed();
	}

	@Override
	protected boolean onMouseClicked(UserInput input) {
		if (InputModifiers.hasShift(input)) {
			return bookmarkOverlay.cycleFavoritePanelDisplayMode(input.isSimulate());
		}
		return bookmarkOverlay.toggleFavoritePanel(input.isSimulate());
	}
}

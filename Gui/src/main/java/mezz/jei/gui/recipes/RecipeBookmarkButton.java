package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.elements.GuiIconToggleButton;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class RecipeBookmarkButton extends GuiIconToggleButton {
	private final BookmarkList bookmarks;
	private final IRecipeLayoutDrawable<?> recipeLayout;
	private final @Nullable RecipeBookmark<?, ?> recipeBookmark;
	private final @Nullable RecipeBookmark<?, ?> recipeBookmarkPreservingAmount;
	private final Runnable showBookmarkPanel;
	private final InputSlotSelectionState inputSlotSelectionState;

	public static RecipeBookmarkButton create(
		IRecipeLayoutDrawable<?> recipeLayout,
		IIngredientManager ingredientManager,
		BookmarkList bookmarks,
		Runnable showBookmarkPanel,
		InputSlotSelectionState inputSlotSelectionState
	) {
		RecipeBookmark<?, ?> recipeBookmark = RecipeBookmark.create(recipeLayout, ingredientManager);
		RecipeBookmark<?, ?> recipeBookmarkPreservingAmount = RecipeBookmark.create(recipeLayout, ingredientManager, true);

		Textures textures = Internal.getTextures();
		IDrawable icon = textures.getRecipeBookmark();
		Rect2i area = recipeLayout.getRecipeBookmarkButtonArea();
		Rect2i layoutArea = recipeLayout.getRect();
		area.setX(area.getX() + layoutArea.getX());
		area.setY(area.getY() + layoutArea.getY());

		RecipeBookmarkButton recipeBookmarkButton = new RecipeBookmarkButton(
			icon,
			bookmarks,
			recipeLayout,
			recipeBookmark,
			recipeBookmarkPreservingAmount,
			showBookmarkPanel,
			inputSlotSelectionState
		);
		recipeBookmarkButton.updateBounds(area);
		return recipeBookmarkButton;
	}

	private RecipeBookmarkButton(
		IDrawable icon,
		BookmarkList bookmarks,
		IRecipeLayoutDrawable<?> recipeLayout,
		@Nullable RecipeBookmark<?, ?> recipeBookmark,
		@Nullable RecipeBookmark<?, ?> recipeBookmarkPreservingAmount,
		Runnable showBookmarkPanel,
		InputSlotSelectionState inputSlotSelectionState
	) {
		super(icon, icon);

		this.bookmarks = bookmarks;
		this.recipeLayout = recipeLayout;
		this.recipeBookmark = recipeBookmark;
		this.recipeBookmarkPreservingAmount = recipeBookmarkPreservingAmount;
		this.showBookmarkPanel = showBookmarkPanel;
		this.inputSlotSelectionState = inputSlotSelectionState;

		if (recipeBookmark == null) {
			button.active = false;
			button.visible = false;
		}
	}

	@Override
	protected void getTooltips(JeiTooltip tooltip) {
		if (recipeBookmark != null) {
			if (bookmarks.contains(recipeBookmark)) {
				tooltip.add(Component.translatable("jei.tooltip.bookmarks.recipe.remove"));
			} else {
				tooltip.add(Component.translatable("jei.tooltip.bookmarks.recipe.add"));
			}
			RecipeButtonHotkeyTooltipUtil.addBookmarkButtonHotkeys(tooltip, Internal.getKeyMappings(), false);
		}
	}

	@Override
	protected boolean isIconToggledOn() {
		return recipeBookmark != null && bookmarks.contains(recipeBookmark);
	}

	@Override
	protected boolean onMouseClicked(UserInput input) {
		return toggleBookmark(input, false);
	}

	public boolean toggleBookmark(UserInput input, boolean preserveAmount) {
		RecipeBookmark<?, ?> bookmark = preserveAmount ? recipeBookmarkPreservingAmount : recipeBookmark;
		if (bookmark != null) {
			boolean wasBookmarked = bookmarks.contains(bookmark);
			if (!input.isSimulate()) {
				bookmarks.toggleRecipeBookmark(recipeLayout, preserveAmount);
				if (!wasBookmarked) {
					showBookmarkPanel.run();
				}
			}
			return true;
		}
		return false;
	}

	public boolean addRecipeBookmarkGroup(UserInput input, boolean preserveAmount) {
		if (!input.isSimulate()) {
			boolean added = bookmarks.addRecipeBookmarks(recipeLayout, preserveAmount, inputSlotSelectionState.selectedKeys());
			if (added) {
				showBookmarkPanel.run();
			}
			return added;
		}
		return recipeBookmark != null;
	}
}

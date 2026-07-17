package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.config.FavoriteRecipeConfig;
import mezz.jei.gui.elements.GuiIconToggleButton;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.favorites.FavoriteTreeBookmarkWriter;
import mezz.jei.gui.favorites.FavoriteTreeBuilder;
import mezz.jei.gui.favorites.FavoriteTreeRecipeLayoutResolver;
import mezz.jei.gui.input.BookmarkKeyInputs;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

public class RecipeFavoriteButton extends GuiIconToggleButton {
	private static final int SELECTED_RESULT_COLOR = 0x66333333;

	private final IRecipeLayoutDrawable<?> recipeLayout;
	private final IDrawable icon;
	private final FavoriteRecipeStore favoriteRecipes;
	private final FavoriteRecipeConfig favoriteRecipeConfig;
	private final Runnable showFavoritePanel;
	private final Runnable showBookmarkPanel;
	private final FavoriteTreeBookmarkWriter favoriteTreeBookmarkWriter;
	private final InputSlotSelectionState inputSlotSelectionState;
	private final IClientConfig clientConfig;
	private final FavoriteRecipeTargetSelector targetSelector;
	private final @Nullable FocusedRecipe focusedRecipe;
	private boolean favorite;
	private boolean selectingTarget;

	public static RecipeFavoriteButton create(
		IRecipeLayoutDrawable<?> recipeLayout,
		IIngredientManager ingredientManager,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		BookmarkList bookmarks,
		FavoriteRecipeStore favoriteRecipes,
		FavoriteRecipeConfig favoriteRecipeConfig,
		Runnable showBookmarkPanel,
		Runnable showFavoritePanel,
		InputSlotSelectionState inputSlotSelectionState
	) {
		FocusedRecipe focusedRecipe = getFocusedRecipe(recipeLayout);
		BookmarkIngredientKey storedTarget = focusedRecipe == null ? null : favoriteRecipes.getManualFavorite(focusedRecipe).orElse(null);
		FavoriteRecipeTargetSelector targetSelector = FavoriteRecipeTargetSelector.create(
			recipeLayout,
			ingredient -> BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager),
			storedTarget
		);
		FavoriteTreeRecipeLayoutResolver treeRecipeResolver = new FavoriteTreeRecipeLayoutResolver(
			recipeManager,
			focusFactory,
			ingredientManager
		);
		FavoriteTreeBookmarkWriter favoriteTreeBookmarkWriter = new FavoriteTreeBookmarkWriter(
			new FavoriteTreeBuilder(favoriteRecipes, treeRecipeResolver),
			treeRecipeResolver::resolveLayout,
			bookmarks::addRecipeLayoutProjectionBookmarkGroup
		);

		Textures textures = Internal.getTextures();
		IDrawable icon = textures.getRecipeFavorite();

		RecipeFavoriteButton button = new RecipeFavoriteButton(
			recipeLayout,
			icon,
			favoriteRecipes,
			favoriteRecipeConfig,
			showBookmarkPanel,
			showFavoritePanel,
			favoriteTreeBookmarkWriter,
			inputSlotSelectionState,
			Internal.getJeiClientConfigs().getClientConfig(),
			targetSelector,
			focusedRecipe
		);
		Rect2i layoutArea = recipeLayout.getRect();
		Rect2i transferArea = offset(recipeLayout.getRecipeTransferButtonArea(), layoutArea);
		Rect2i bookmarkArea = offset(recipeLayout.getRecipeBookmarkButtonArea(), layoutArea);
		button.updateBounds(RecipeGuiLayouts.calculateRecipeFavoriteButtonArea(transferArea, bookmarkArea));
		return button;
	}

	private RecipeFavoriteButton(
		IRecipeLayoutDrawable<?> recipeLayout,
		IDrawable icon,
		FavoriteRecipeStore favoriteRecipes,
		FavoriteRecipeConfig favoriteRecipeConfig,
		Runnable showBookmarkPanel,
		Runnable showFavoritePanel,
		FavoriteTreeBookmarkWriter favoriteTreeBookmarkWriter,
		InputSlotSelectionState inputSlotSelectionState,
		IClientConfig clientConfig,
		FavoriteRecipeTargetSelector targetSelector,
		@Nullable FocusedRecipe focusedRecipe
	) {
		super(icon, icon);
		this.recipeLayout = recipeLayout;
		this.icon = icon;
		this.favoriteRecipes = favoriteRecipes;
		this.favoriteRecipeConfig = favoriteRecipeConfig;
		this.showBookmarkPanel = showBookmarkPanel;
		this.showFavoritePanel = showFavoritePanel;
		this.favoriteTreeBookmarkWriter = favoriteTreeBookmarkWriter;
		this.inputSlotSelectionState = inputSlotSelectionState;
		this.clientConfig = clientConfig;
		this.targetSelector = targetSelector;
		this.focusedRecipe = focusedRecipe;

		if (targetSelector.targetCount() == 0 || focusedRecipe == null) {
			button.active = false;
			button.visible = false;
		}
		tick();
	}

	@Override
	protected void getTooltips(JeiTooltip tooltip) {
		if (targetSelector.selectedKey().isPresent()) {
			if (favorite) {
				tooltip.add(Component.translatable("jei.tooltip.recipe.favorite.remove"));
			} else {
				tooltip.add(Component.translatable("jei.tooltip.recipe.favorite.add"));
			}
			RecipeButtonHotkeyTooltipUtil.addFavoriteButtonHotkeys(tooltip, Internal.getKeyMappings(), targetSelector.hasMultipleTargets());
		}
	}

	@Override
	public void tick() {
		BookmarkIngredientKey storedTarget = getStoredTarget();
		if (!selectingTarget) {
			targetSelector.selectStoredOrDefault(storedTarget);
		}
		favorite = targetSelector.isSelectedTarget(storedTarget);
	}

	@Override
	protected boolean isIconToggledOn() {
		return favorite;
	}

	@Override
	protected boolean onMouseClicked(UserInput input) {
		if (focusedRecipe == null) {
			return false;
		}
		Optional<BookmarkIngredientKey> selectedTarget = targetSelector.selectedKey();
		if (selectedTarget.isEmpty()) {
			return false;
		}
		if (!input.isSimulate()) {
			boolean added = toggleFavorite(favoriteRecipes, focusedRecipe, selectedTarget.get());
			if (added) {
				showFavoritePanel.run();
			}
			favoriteRecipeConfig.saveFavorites(favoriteRecipes);
			tick();
		}
		return true;
	}

	@Override
	public void draw(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		selectingTarget = isMouseOver(mouseX, mouseY);
		if (!selectingTarget) {
			targetSelector.selectStoredOrDefault(getStoredTarget());
		}
		tick();
		drawSelectedResultOverlay(guiGraphics);
		super.draw(guiGraphics, mouseX, mouseY, partialTicks);
		if (favorite) {
			guiGraphics.fill(
				RenderType.gui(),
				button.getX(),
				button.getY(),
				button.getX() + button.getWidth(),
				button.getY() + button.getHeight(),
				0x1100FF00
			);
		}
	}

	@Override
	public IUserInputHandler createInputHandler() {
		return new CombinedInputHandler(
			"RecipeFavoriteButton",
			new FavoriteOutputSlotInputHandler(),
			new SaveFavoriteTreeInputHandler(),
			super.createInputHandler(),
			new ScrollInputHandler()
		);
	}

	public boolean isFavorite() {
		return favorite;
	}

	public static boolean toggleFavorite(
		FavoriteRecipeStore favoriteRecipes,
		FocusedRecipe focusedRecipe,
		BookmarkIngredientKey selectedTarget
	) {
		Optional<BookmarkIngredientKey> storedTarget = favoriteRecipes.getManualFavorite(focusedRecipe);
		if (storedTarget.filter(selectedTarget::equals).isPresent()) {
			favoriteRecipes.removeFavorite(focusedRecipe);
			return false;
		}
		favoriteRecipes.setFavorite(selectedTarget, focusedRecipe);
		return true;
	}

	public static Optional<FavoriteOutputSlotAction> resolveFavoriteOutputSlotAction(
		UserInput input,
		IInternalKeyMappings keyBindings
	) {
		int modifiers = input.getModifiers();
		if (hasControl(modifiers) || hasAlt(modifiers)) {
			return Optional.empty();
		}
		if (!keyBindings.getFavoriteRecipe().matchesIgnoringModifiers(input.getKey())) {
			return Optional.empty();
		}
		if (hasShift(modifiers)) {
			return Optional.of(FavoriteOutputSlotAction.SAVE_FAVORITE_TREE);
		}
		return Optional.of(FavoriteOutputSlotAction.TOGGLE_FAVORITE);
	}

	private static boolean hasShift(int modifiers) {
		return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
	}

	private static boolean hasControl(int modifiers) {
		return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
	}

	private static boolean hasAlt(int modifiers) {
		return (modifiers & GLFW.GLFW_MOD_ALT) != 0;
	}

	public enum FavoriteOutputSlotAction {
		TOGGLE_FAVORITE,
		SAVE_FAVORITE_TREE
	}

	private @Nullable BookmarkIngredientKey getStoredTarget() {
		if (focusedRecipe == null) {
			return null;
		}
		return favoriteRecipes.getManualFavorite(focusedRecipe).orElse(null);
	}

	@SuppressWarnings("removal")
	private void drawSelectedResultOverlay(GuiGraphics guiGraphics) {
		if (!selectingTarget) {
			return;
		}
		targetSelector.selectedSlot().ifPresent(slot -> {
			Rect2i layoutArea = recipeLayout.getRect();
			Rect2i slotArea = slot.getRect();
			int x = layoutArea.getX() + slotArea.getX();
			int y = layoutArea.getY() + slotArea.getY();
			guiGraphics.fill(RenderType.gui(), x, y, x + slotArea.getWidth(), y + slotArea.getHeight(), SELECTED_RESULT_COLOR);
			guiGraphics.pose().pushPose();
			guiGraphics.pose().translate(
				x + (slotArea.getWidth() - icon.getWidth()) / 2.0,
				y + (slotArea.getHeight() - icon.getHeight()) / 2.0,
				0
			);
			icon.draw(guiGraphics);
			guiGraphics.pose().popPose();
		});
	}

	private static <R> @Nullable FocusedRecipe getFocusedRecipe(IRecipeLayoutDrawable<R> recipeLayout) {
		R recipe = recipeLayout.getRecipe();
		ResourceLocation recipeUid = recipeLayout.getRecipeCategory().getRegistryName(recipe);
		if (recipeUid == null) {
			return null;
		}
		ResourceLocation recipeTypeUid = recipeLayout.getRecipeCategory().getRecipeType().getUid();
		return new FocusedRecipe(recipeTypeUid, recipeUid);
	}

	private static Rect2i offset(Rect2i area, Rect2i offset) {
		return new Rect2i(
			area.getX() + offset.getX(),
			area.getY() + offset.getY(),
			area.getWidth(),
			area.getHeight()
		);
	}

	private class FavoriteOutputSlotInputHandler implements IUserInputHandler {
		@Override
		public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
			if (focusedRecipe == null) {
				return Optional.empty();
			}
			Optional<FavoriteOutputSlotAction> action = resolveFavoriteOutputSlotAction(input, keyBindings);
			if (action.isEmpty()) {
				return Optional.empty();
			}
			Optional<BookmarkIngredientKey> target = recipeLayout.getSlotUnderMouse(input.getMouseX(), input.getMouseY())
				.map(RecipeSlotUnderMouse::slot)
				.filter(slot -> slot.getRole() == RecipeIngredientRole.OUTPUT)
				.flatMap(targetSelector::keyForSlot);
			if (target.isEmpty()) {
				return Optional.empty();
			}
			if (input.isSimulate()) {
				return Optional.of(this);
			}
			switch (action.get()) {
				case TOGGLE_FAVORITE -> {
					boolean added = toggleFavorite(favoriteRecipes, focusedRecipe, target.get());
					if (added) {
						showFavoritePanel.run();
					}
					favoriteRecipeConfig.saveFavorites(favoriteRecipes);
					tick();
				}
				case SAVE_FAVORITE_TREE -> {
					if (saveFavoriteTree(Optional.of(target.get())).isEmpty()) {
						return Optional.empty();
					}
				}
			}
			JeiClientSoundUtil.playClickSound();
			return Optional.of(this);
		}
	}

	private class SaveFavoriteTreeInputHandler implements IUserInputHandler {
		@Override
		public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
			if (focusedRecipe == null || !isSaveFavoriteTreeInput(input, keyBindings)) {
				return Optional.empty();
			}
			if (input.isSimulate()) {
				return Optional.of(this);
			}
			if (saveFavoriteTree(targetSelector.selectedKey()).isEmpty()) {
				return Optional.empty();
			}
			JeiClientSoundUtil.playClickSound();
			return Optional.of(this);
		}

		private boolean isSaveFavoriteTreeInput(UserInput input, IInternalKeyMappings keyBindings) {
			return isMouseOver(input.getMouseX(), input.getMouseY()) &&
				BookmarkKeyInputs.isShiftBookmarkKey(input, keyBindings);
		}
	}

	private Optional<String> saveFavoriteTree(Optional<BookmarkIngredientKey> selectedOutputKey) {
		Optional<String> groupId = favoriteTreeBookmarkWriter.save(
			focusedRecipe,
			clientConfig.getFavoriteTreeDepth(),
			selectedOutputKey,
			inputSlotSelectionState.selectedKeys()
		);
		groupId.ifPresent(ignored -> showBookmarkPanel.run());
		return groupId;
	}

	private class ScrollInputHandler implements IUserInputHandler {
		@Override
		public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
			return Optional.empty();
		}

		@Override
		public Optional<IUserInputHandler> handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDelta) {
			if (!isMouseOver(mouseX, mouseY)) {
				return Optional.empty();
			}
			if (targetSelector.scroll(scrollDelta)) {
				selectingTarget = true;
				tick();
				return Optional.of(this);
			}
			return Optional.empty();
		}
	}
}

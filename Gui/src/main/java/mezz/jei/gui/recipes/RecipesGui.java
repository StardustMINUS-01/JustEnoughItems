package mezz.jei.gui.recipes;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.Internal;
import mezz.jei.common.config.DebugConfig;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.gui.elements.ScalableDrawable;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.common.util.StringUtil;
import mezz.jei.gui.GuiProperties;
import mezz.jei.gui.bookmarks.BookmarkFactory;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator.ClientFallbackStarter;
import mezz.jei.gui.config.FavoriteRecipeConfig;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.favorites.FavoriteRecipeInputs;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.favorites.FavoriteTreeBookmarkWriter;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IDraggableIngredientInternal;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.FocusedRecipeCandidate;
import mezz.jei.gui.input.IRecipeFocusSource;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.MouseUtil;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.UserInputRouter;
import mezz.jei.gui.input.handlers.NullInputHandler;
import mezz.jei.gui.input.handlers.ProxyInputHandler;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistory;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;
import mezz.jei.gui.recipes.navigation.RecipeNavigationButtonController;
import mezz.jei.gui.recipes.navigation.RecipeNavigationDirection;
import mezz.jei.gui.recipes.filtering.RecipeFilterModeButtonController;
import mezz.jei.gui.recipes.filtering.RecipeFilterSettings;
import mezz.jei.gui.recipes.filtering.RecipeSearchInputHandler;
import mezz.jei.gui.recipes.filtering.RecipeSearchTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class RecipesGui extends Screen implements IRecipesGui, IRecipeFocusSource {
	private static final int borderPadding = 6;
	private static final int minRecipePadding = 4;
	private static final int navBarPadding = 2;
	private static final int titleInnerPadding = 14;
	private static final int smallButtonWidth = 13;
	private static final int smallButtonHeight = 13;
	private static final int minGuiWidth = 198;
	private static final int topBarControlGap = 2;
	private static final int filterModeButtonWidth = 20;

	private final IInternalKeyMappings keyBindings;
	private final BookmarkList bookmarks;
	private final IFocusFactory focusFactory;
	private final IRecipeManager recipeManager;
	private final IIngredientManager ingredientManager;
	private final FavoriteRecipeStore favoriteRecipes;
	private final FavoriteRecipeConfig favoriteRecipeConfig;
	private final FavoriteTreeBookmarkWriter favoriteTreeBookmarkWriter;
	private final Map<FocusedRecipe, Map<Integer, FavoriteRecipeStore.FavoriteSlotInput>> pendingFavoriteInputs = new HashMap<>();
	private final ClientFallbackStarter clientFallbackStarter;
	private final Runnable showBookmarkPanel;
	private final Runnable showFavoritePanel;

	private int headerHeight;

	/* Internal logic for the gui, handles finding recipes */
	private final IRecipeGuiLogic logic;
	private final RecipeGuiLogic navigationLogic;

	/* List of RecipeLayout to display */
	private final RecipeGuiLayouts layouts;

	private String pageString = "1/1";
	private final ScalableDrawable background;

	private final RecipeCatalysts recipeCatalysts;
	private final RecipeGuiTabs recipeGuiTabs;
	private final RecipeOptionButtons optionButtons;
	private final UserInputRouter inputHandler;
	private final RecipeFilterSettings filterSettings = new RecipeFilterSettings();
	private final RecipeSearchTextField recipeSearchField;
	private final RecipeSearchInputHandler recipeSearchInputHandler;
	private final IconButton filterModeButton;
	private final IconButton backNavigation;
	private final IconButton forwardNavigation;

	private final IconButton nextRecipeCategory;
	private final IconButton previousRecipeCategory;
	private final IconButton nextPage;
	private final IconButton previousPage;

	@Nullable
	private Screen parentScreen;
	/**
	 * The GUI tries to size itself to this ideal area.
	 * This is a stable place to anchor buttons so that
	 * they don't move when the GUI resizes.
	 */
	private ImmutableRect2i idealArea = ImmutableRect2i.EMPTY;
	/**
	 * This is the actual are of the GUI, which temporarily
	 * stretches to fit large recipes.
	 */
	private ImmutableRect2i area = ImmutableRect2i.EMPTY;

	private RecipeCategoryTitle recipeCategoryTitle = new RecipeCategoryTitle();

	private boolean init = false;
	private boolean preserveNavigationOnScreenRemoval;

	public RecipesGui(
		IRecipeManager recipeManager,
		IIngredientManager ingredientManager,
		IRecipeTransferManager recipeTransferManager,
		IInternalKeyMappings keyBindings,
		IFocusFactory focusFactory,
		BookmarkList bookmarks,
		LookupHistory lookupHistory,
		IGuiHelper guiHelper,
		BookmarkFactory bookmarkFactory,
		FavoriteRecipeStore favoriteRecipes,
		FavoriteRecipeConfig favoriteRecipeConfig,
		FavoriteTreeBookmarkWriter favoriteTreeBookmarkWriter,
		ClientFallbackStarter clientFallbackStarter,
		Runnable showBookmarkPanel,
		Runnable showFavoritePanel
	) {
		this(
			recipeManager,
			ingredientManager,
			recipeTransferManager,
			keyBindings,
			focusFactory,
			bookmarks,
			lookupHistory,
			guiHelper,
			bookmarkFactory,
			favoriteRecipes,
			favoriteRecipeConfig,
			favoriteTreeBookmarkWriter,
			clientFallbackStarter,
			showBookmarkPanel,
			showFavoritePanel,
			() -> RecipePreferenceRules.EMPTY
		);
	}

	public RecipesGui(
		IRecipeManager recipeManager,
		IIngredientManager ingredientManager,
		IRecipeTransferManager recipeTransferManager,
		IInternalKeyMappings keyBindings,
		IFocusFactory focusFactory,
		BookmarkList bookmarks,
		LookupHistory lookupHistory,
		IGuiHelper guiHelper,
		BookmarkFactory bookmarkFactory,
		FavoriteRecipeStore favoriteRecipes,
		FavoriteRecipeConfig favoriteRecipeConfig,
		FavoriteTreeBookmarkWriter favoriteTreeBookmarkWriter,
		ClientFallbackStarter clientFallbackStarter,
		Runnable showBookmarkPanel,
		Runnable showFavoritePanel,
		Supplier<RecipePreferenceRules> preferenceRulesSupplier
	) {
		super(Component.literal("Recipes"));
		this.recipeManager = recipeManager;
		this.ingredientManager = ingredientManager;
		this.bookmarks = bookmarks;
		this.favoriteRecipes = favoriteRecipes;
		this.favoriteRecipeConfig = favoriteRecipeConfig;
		this.favoriteTreeBookmarkWriter = favoriteTreeBookmarkWriter;
		this.clientFallbackStarter = clientFallbackStarter;
		this.showBookmarkPanel = showBookmarkPanel;
		this.showFavoritePanel = showFavoritePanel;
		this.keyBindings = keyBindings;
		this.navigationLogic = new RecipeGuiLogic(
			recipeManager,
			ingredientManager,
			lookupHistory,
			recipeTransferManager,
			this::updateLayout,
			focusFactory,
			bookmarkFactory,
			preferenceRulesSupplier
		);
		this.logic = navigationLogic;
		this.recipeCatalysts = new RecipeCatalysts(recipeManager);
		this.recipeGuiTabs = new RecipeGuiTabs(this.logic, recipeManager, guiHelper);
		this.optionButtons = new RecipeOptionButtons(this.logic::goToFirstPage);
		this.recipeSearchField = new RecipeSearchTextField();
		this.recipeSearchField.setResponder(filterSettings::setDraftQuery);
		this.recipeSearchInputHandler = new RecipeSearchInputHandler(
			recipeSearchField,
			filterSettings,
			this::applyRecipeResultFilter
		);
		this.filterModeButton = new IconButton(
			new RecipeFilterModeButtonController(filterSettings, this::applyRecipeResultFilter)
		);
		this.focusFactory = focusFactory;
		this.minecraft = Minecraft.getInstance();
		this.layouts = new RecipeGuiLayouts();
		IClientConfig clientConfig = Internal.getJeiClientConfigs().getClientConfig();
		clientConfig.centerSearchBarEnabled().addListener(v -> reopenIfOpen());
		clientConfig.maxRecipeGuiHeight().addListener(v -> reopenIfOpen());

		Textures textures = Internal.getTextures();
		IDrawableStatic arrowNext = textures.getArrowNext();
		IDrawableStatic arrowPrevious = textures.getArrowPrevious();

		ImmutableRect2i buttonSize = new ImmutableRect2i(0, 0, smallButtonWidth, smallButtonHeight);

		nextRecipeCategory = new IconButton(
			new IIconButtonController() {
				@Override
				public boolean onPress(IJeiUserInput input) {
					return input.isSimulate() || logic.nextRecipeCategory();
				}

				@Override
				public void initState(IButtonState state) {
					state.setIcon(arrowNext);
					updateState(state);
				}

				@Override
				public void updateState(IButtonState state) {
					state.setActive(logic.hasMultipleCategories());
				}
			},
			buttonSize
		);
		backNavigation = new IconButton(
			new RecipeNavigationButtonController(
				RecipeNavigationDirection.BACK,
				this::canNavigate,
				this::getNavigationTargetTitle,
				this::navigate
			),
			buttonSize
		);
		forwardNavigation = new IconButton(
			new RecipeNavigationButtonController(
				RecipeNavigationDirection.FORWARD,
				this::canNavigate,
				this::getNavigationTargetTitle,
				this::navigate
			),
			buttonSize
		);
		previousRecipeCategory = new IconButton(
			new IIconButtonController() {
				@Override
				public boolean onPress(IJeiUserInput input) {
					return input.isSimulate() || logic.previousRecipeCategory();
				}

				@Override
				public void initState(IButtonState state) {
					state.setIcon(arrowPrevious);
					updateState(state);
				}

				@Override
				public void updateState(IButtonState state) {
					state.setActive(logic.hasMultipleCategories());
				}
			},
			buttonSize
		);
		nextPage = new IconButton(
			new IIconButtonController() {
				@Override
				public boolean onPress(IJeiUserInput input) {
					return input.isSimulate() || logic.nextPage();
				}

				@Override
				public void initState(IButtonState state) {
					state.setIcon(arrowNext);
					updateState(state);
				}

				@Override
				public void updateState(IButtonState state) {
					state.setActive(logic.hasMultiplePages());
				}
			},
			buttonSize
		);
		previousPage = new IconButton(
			new IIconButtonController() {
				@Override
				public boolean onPress(IJeiUserInput input) {
					return input.isSimulate() || logic.previousPage();
				}

				@Override
				public void initState(IButtonState state) {
					state.setIcon(arrowPrevious);
					updateState(state);
				}

				@Override
				public void updateState(IButtonState state) {
					state.setActive(logic.hasMultiplePages());
				}
			},
			buttonSize
		);

		background = textures.getRecipeGuiBackground();

		IUserInputHandler optionButtonsInputHandler = optionButtons.createInputHandler();
		inputHandler = new UserInputRouter(
			"RecipesGui",
			recipeSearchInputHandler,
			filterModeButton.createInputHandler(),
			layouts.createInputHandler(),
			new UserInputHandler(this),
			new ProxyInputHandler(() -> logic.hasRecipeResults() ? optionButtonsInputHandler : NullInputHandler.INSTANCE),
			recipeGuiTabs.createInputHandler(),
			nextRecipeCategory.createInputHandler(),
			previousRecipeCategory.createInputHandler(),
			backNavigation.createInputHandler(),
			forwardNavigation.createInputHandler(),
			nextPage.createInputHandler(),
			previousPage.createInputHandler()
		);
	}

	public ImmutableRect2i getArea() {
		return this.area;
	}

	public int getLeftSideExtraWidth() {
		if (!logic.hasRecipeResults()) {
			return 0;
		}
		if (recipeCatalysts.isEmpty()) {
			return optionButtons.getWidth();
		}
		return Math.max(recipeCatalysts.getWidth(), optionButtons.getWidth());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void init() {
		super.init();

		final int xSize = minGuiWidth;
		IClientConfig clientConfig = Internal.getJeiClientConfigs().getClientConfig();
		RecipeGuiSizing.Size recipeGuiSize = RecipeGuiSizing.calculateInitialSize(
			this.height,
			clientConfig.centerSearchBarEnabled().getValue(),
			clientConfig.maxRecipeGuiHeight().getValue()
		);
		int ySize = recipeGuiSize.ySize();
		int extraSpace = recipeGuiSize.extraSpace();

		final int guiLeft = (this.width - xSize) / 2;
		final int guiTop = RecipeGuiTab.TAB_HEIGHT + 21 + (extraSpace / 2);

		this.idealArea = new ImmutableRect2i(guiLeft, guiTop, xSize, ySize);
		this.area = this.idealArea;

		final int rightButtonX = guiLeft + xSize - borderPadding - smallButtonWidth;
		final int leftButtonX = guiLeft + borderPadding;

		int titleHeight = font.lineHeight + borderPadding;
		int recipeClassButtonTop = guiTop + titleHeight - smallButtonHeight + navBarPadding;
		nextRecipeCategory.updateBounds(nextRecipeCategory.getArea().setPosition(rightButtonX, recipeClassButtonTop));
		previousRecipeCategory.updateBounds(previousRecipeCategory.getArea().setPosition(leftButtonX, recipeClassButtonTop));
		int forwardNavigationX = rightButtonX - topBarControlGap - smallButtonWidth;
		int backNavigationX = forwardNavigationX - topBarControlGap - smallButtonWidth;
		forwardNavigation.updateBounds(forwardNavigation.getArea().setPosition(forwardNavigationX, recipeClassButtonTop));
		backNavigation.updateBounds(backNavigation.getArea().setPosition(backNavigationX, recipeClassButtonTop));

		int pageButtonTop = recipeClassButtonTop + smallButtonHeight + navBarPadding;
		nextPage.updateBounds(nextPage.getArea().setPosition(rightButtonX, pageButtonTop));
		previousPage.updateBounds(previousPage.getArea().setPosition(leftButtonX, pageButtonTop));

		this.headerHeight = (pageButtonTop + smallButtonHeight) - guiTop;

		this.init = true;
		updateLayout();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		if (minecraft == null) {
			return;
		}
		super.render(guiGraphics, mouseX, mouseY, partialTicks);

		renderTransparentBackground(guiGraphics);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		this.background.draw(guiGraphics, area);

		RenderSystem.disableBlend();

		guiGraphics.fill(
			RenderType.gui(),
			previousRecipeCategory.getX() + previousRecipeCategory.getWidth(),
			previousRecipeCategory.getY(),
			nextRecipeCategory.getX(),
			nextRecipeCategory.getY() + nextRecipeCategory.getHeight(),
			0x30000000
		);
		guiGraphics.fill(
			RenderType.gui(),
			previousPage.getX() + previousPage.getWidth(),
			previousPage.getY(),
			nextPage.getX(),
			nextPage.getY() + nextPage.getHeight(),
			0x30000000
		);

		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		this.recipeCategoryTitle.draw(guiGraphics, font);

		ImmutableRect2i pageArea = MathUtil.union(previousPage.getArea(), nextPage.getArea());
		StringUtil.drawCenteredStringWithShadow(guiGraphics, font, pageString, pageArea);

		nextRecipeCategory.draw(guiGraphics, mouseX, mouseY, partialTicks);
		previousRecipeCategory.draw(guiGraphics, mouseX, mouseY, partialTicks);
		backNavigation.draw(guiGraphics, mouseX, mouseY, partialTicks);
		forwardNavigation.draw(guiGraphics, mouseX, mouseY, partialTicks);
		nextPage.draw(guiGraphics, mouseX, mouseY, partialTicks);
		previousPage.draw(guiGraphics, mouseX, mouseY, partialTicks);

		Optional<IRecipeLayoutDrawable<?>> hoveredRecipeLayout = Optional.empty();
		Optional<IRecipeSlotDrawable> hoveredRecipeCatalyst = Optional.empty();
		if (logic.hasRecipeResults()) {
			hoveredRecipeLayout = this.layouts.draw(guiGraphics, mouseX, mouseY);
			optionButtons.draw(guiGraphics, mouseX, mouseY, partialTicks);
			hoveredRecipeCatalyst = recipeCatalysts.draw(guiGraphics, mouseX, mouseY);
		} else {
			drawEmptyRecipeResults(guiGraphics);
		}

		recipeGuiTabs.draw(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
		filterModeButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
		recipeSearchField.render(guiGraphics, mouseX, mouseY, partialTicks);
		filterModeButton.drawTooltips(guiGraphics, mouseX, mouseY);
		backNavigation.drawTooltips(guiGraphics, mouseX, mouseY);
		forwardNavigation.drawTooltips(guiGraphics, mouseX, mouseY);

		if (logic.hasRecipeResults()) {
			this.layouts.drawTooltips(guiGraphics, mouseX, mouseY);
			optionButtons.drawTooltips(guiGraphics, mouseX, mouseY);
		}
		RenderSystem.disableBlend();

		hoveredRecipeLayout.ifPresent(l -> l.drawOverlays(guiGraphics, mouseX, mouseY));

		hoveredRecipeCatalyst.ifPresent(h -> {
			h.drawTooltip(guiGraphics, mouseX, mouseY);
		});
		RenderSystem.enableDepthTest();

		if (recipeCategoryTitle.isMouseOver(mouseX, mouseY)) {
			JeiTooltip tooltip = new JeiTooltip();
			recipeCategoryTitle.getTooltip(tooltip);
			if (!logic.hasAllCategories()) {
				tooltip.addKeyUsageComponent("jei.tooltip.show.all.recipes.hotkey", keyBindings.getLeftClick());
			}
			tooltip.draw(guiGraphics, mouseX, mouseY);
		}

		if (DebugConfig.isDebugGuisEnabled()) {
			guiGraphics.fill(
				RenderType.gui(),
				idealArea.getX(),
				idealArea.getY(),
				idealArea.getX() + idealArea.getWidth(),
				idealArea.getY() + idealArea.getHeight(),
				0x4400FF00
			);

			guiGraphics.fill(
				RenderType.gui(),
				area.getX(),
				area.getY(),
				area.getX() + area.getWidth(),
				area.getY() + area.getHeight(),
				0x44990044
			);

			ImmutableRect2i recipeLayoutsArea = getRecipeLayoutsArea();
			guiGraphics.fill(
				RenderType.gui(),
				recipeLayoutsArea.getX(),
				recipeLayoutsArea.getY(),
				recipeLayoutsArea.getX() + recipeLayoutsArea.getWidth(),
				recipeLayoutsArea.getY() + recipeLayoutsArea.getHeight(),
				0x44228844
			);
		}
	}

	private void drawEmptyRecipeResults(GuiGraphics guiGraphics) {
		ImmutableRect2i contentArea = getRecipeLayoutsArea();
		int centerX = contentArea.getX() + (contentArea.getWidth() / 2);
		int totalHeight = (font.lineHeight * 2) + 8 + font.lineHeight;
		int top = contentArea.getY() + ((contentArea.getHeight() - totalHeight) / 2);

		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(centerX, top, 0);
		guiGraphics.pose().scale(2.0F, 2.0F, 1.0F);
		guiGraphics.drawString(font, "UwU", -font.width("UwU") / 2, 0, 0xFFFFFFFF, true);
		guiGraphics.pose().popPose();

		Component hint = Component.translatable("gui.jei.recipe_filter.empty");
		int hintY = top + (font.lineHeight * 2) + 8;
		guiGraphics.drawCenteredString(font, hint, centerX, hintY, 0xFFAAAAAA);
	}

	private static ImmutableRect2i calculateAreaToFitLayouts(ImmutableRect2i idealArea, int screenWidth, int recipeWidth) {
		if (recipeWidth == 0) {
			return idealArea;
		}
		final int padding = 2 * borderPadding;
		int width = minGuiWidth - padding;

		width = Math.max(recipeWidth, width);

		final int newWidth = width + padding;
		final int newX = (screenWidth - newWidth) / 2;

		return new ImmutableRect2i(
			newX,
			idealArea.getY(),
			newWidth,
			idealArea.getHeight()
		);
	}

	@Override
	public void tick() {
		super.tick();

		this.layouts.tick(getParentContainerMenu());
		this.optionButtons.tick();
		this.filterModeButton.tick();
		this.logic.tick();
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		if (minecraft != null && minecraft.screen == this) {
			return area.contains(mouseX, mouseY) ||
				(logic.hasRecipeResults() && optionButtons.getArea().contains(mouseX, mouseY)) ||
				recipeGuiTabs.getTopBarArea().contains(mouseX, mouseY);
		}
		return false;
	}

	@Override
	public Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
		if (isOpen()) {
			return Stream.concat(
				recipeCatalysts.getIngredientUnderMouse(mouseX, mouseY),
				layouts.getIngredientUnderMouse(mouseX, mouseY)
			);
		}
		return Stream.empty();
	}

	@Override
	public Stream<IDraggableIngredientInternal<?>> getDraggableIngredientUnderMouse(double mouseX, double mouseY) {
		return Stream.empty();
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		layouts.mouseMoved(mouseX, mouseY);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double dragX, double dragY) {
		InputConstants.Key input = InputConstants.Type.MOUSE.getOrCreate(mouseButton);
		return layouts.mouseDragged(mouseX, mouseY, input, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.inputHandler.handleMouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		if (recipeSearchField.isFocused() && !recipeSearchField.isMouseOver(mouseX, mouseY)) {
			recipeSearchInputHandler.commitAndUnfocus();
		}
		boolean handled = UserInput.fromVanilla(mouseX, mouseY, mouseButton, InputType.SIMULATE)
			.map(this::handleInput)
			.orElse(false);

		if (handled) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, mouseButton);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
		boolean handled = UserInput.fromVanilla(mouseX, mouseY, mouseButton, InputType.EXECUTE)
			.map(this::handleInput)
			.orElse(false);

		if (handled) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, mouseButton);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		UserInput input = UserInput.fromVanilla(keyCode, scanCode, modifiers, InputType.IMMEDIATE);
		return handleInput(input);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (recipeSearchField.isFocused() && recipeSearchField.charTyped(codePoint, modifiers)) {
			return true;
		}
		return super.charTyped(codePoint, modifiers);
	}

	private boolean handleInput(UserInput input) {
		return this.inputHandler.handleUserInput(this, input, keyBindings);
	}

	public boolean isOpen() {
		return minecraft != null && minecraft.screen == this;
	}

	private void reopenIfOpen() {
		if (isOpen() && minecraft != null) {
			Screen currentParentScreen = parentScreen;
			preserveNavigationOnScreenRemoval = true;
			try {
				minecraft.setScreen(currentParentScreen);
			} finally {
				preserveNavigationOnScreenRemoval = false;
			}
			parentScreen = currentParentScreen;
			open();
		}
	}

	private void open() {
		if (minecraft != null) {
			if (!isOpen()) {
				parentScreen = minecraft.screen;
			}
			minecraft.setScreen(this);
		}
	}

	@Override
	public void onClose() {
		logic.clearRecipeResultSnapshot();
		if (isOpen() && minecraft != null) {
			minecraft.setScreen(parentScreen);
			parentScreen = null;
			logic.clearHistory();
			return;
		}
		super.onClose();
	}

	@Override
	public void removed() {
		super.removed();
		if (!preserveNavigationOnScreenRemoval) {
			logic.clearRecipeResultSnapshot();
			logic.clearHistory();
		}
	}

	@Override
	public void show(List<IFocus<?>> focuses) {
		prepareForLookup();
		IFocusGroup checkedFocuses = focusFactory.createFocusGroup(focuses);
		if (logic.showFocus(checkedFocuses) && !isOpen()) {
			open();
		}
	}

	@Override
	public void showTypes(List<RecipeType<?>> recipeTypes) {
		ErrorUtil.checkNotEmpty(recipeTypes, "recipeTypes");
		prepareForLookup();

		if (logic.showCategories(recipeTypes) && !isOpen()) {
			open();
		}
	}

	@Override
	public <T> void showRecipes(IRecipeCategory<T> recipeCategory, List<T> recipes, List<IFocus<?>> focuses) {
		ErrorUtil.checkNotNull(recipeCategory, "recipeCategory");
		ErrorUtil.checkNotEmpty(recipes, "recipes");
		prepareForLookup();
		IFocusGroup checkedFocuses = focusFactory.createFocusGroup(focuses);

		IFocusedRecipes<T> focusedRecipes = new StaticFocusedRecipes<>(recipeCategory, recipes);
		if (logic.showRecipes(focusedRecipes, checkedFocuses) && !isOpen()) {
			open();
		}
	}

	public <T> void showRecipesWithFavoriteInputs(
		IRecipeCategory<T> recipeCategory,
		List<T> recipes,
		List<IFocus<?>> focuses,
		Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> inputs
	) {
		if (!recipes.isEmpty()) {
			T recipe = recipes.getFirst();
			ResourceLocation recipeUid = recipeCategory.getRegistryName(recipe);
			if (recipeUid != null) {
				FocusedRecipe focusedRecipe = new FocusedRecipe(recipeCategory.getRecipeType().getUid(), recipeUid);
				pendingFavoriteInputs.put(focusedRecipe, Map.copyOf(inputs));
			}
		}
		showRecipes(recipeCategory, recipes, focuses);
	}

	@Override
	public <T> Optional<T> getIngredientUnderMouse(IIngredientType<T> ingredientType) {
		double x = MouseUtil.getX();
		double y = MouseUtil.getY();

		return getIngredientUnderMouse(x, y)
			.map(IClickableIngredientInternal::getTypedIngredient)
			.flatMap(i -> i.getIngredient(ingredientType).stream())
			.findFirst();
	}

	public Optional<IRecipeLayoutWithButtons<?>> getRecipeLayoutUnderMouse(double mouseX, double mouseY) {
		if (!isOpen()) {
			return Optional.empty();
		}
		return layouts.getRecipeLayoutUnderMouse(mouseX, mouseY);
	}

	public Optional<RecipeGuiLayouts.RecipeLayoutUnderMouse> getRecipeLayoutWithSlotUnderMouse(double mouseX, double mouseY) {
		if (!isOpen()) {
			return Optional.empty();
		}
		return layouts.getRecipeLayoutWithSlotUnderMouse(mouseX, mouseY);
	}

	public Optional<FocusedRecipeCandidate> getFocusedRecipeCandidateUnderMouse(double mouseX, double mouseY) {
		if (!isOpen()) {
			return Optional.empty();
		}
		return layouts.getFocusedRecipeCandidateUnderMouse(mouseX, mouseY);
	}

	public Optional<FocusedRecipeCandidate> getRecipeTooltipCandidateUnderMouse(double mouseX, double mouseY) {
		if (!isOpen()) {
			return Optional.empty();
		}
		return layouts.getRecipeTooltipCandidateUnderMouse(mouseX, mouseY);
	}

	public boolean bookmarkRecipeUnderMouse(UserInput input, boolean preserveAmount) {
		return isOpen() && layouts.bookmarkRecipeUnderMouse(input, preserveAmount);
	}

	void showBookmarkPanel() {
		showBookmarkPanel.run();
	}

	RecipeLayoutForkExtras createRecipeLayoutForkExtras(IRecipeLayoutDrawable<?> recipeLayoutDrawable) {
		InputSlotSelectionState inputSlotSelectionState = new InputSlotSelectionState(ingredientManager);
		Optional.ofNullable(getFocusedRecipe(recipeLayoutDrawable))
			.map(pendingFavoriteInputs::remove)
			.ifPresent(inputs -> FavoriteRecipeInputs.apply(recipeLayoutDrawable, inputs, inputSlotSelectionState));
		RecipeFavoriteButton favoriteButton = RecipeFavoriteButton.create(
			recipeLayoutDrawable,
			ingredientManager,
			favoriteTreeBookmarkWriter,
			favoriteRecipes,
			favoriteRecipeConfig,
			showBookmarkPanel,
			showFavoritePanel,
			inputSlotSelectionState
		);
		return new RecipeLayoutForkExtras(
			favoriteButton,
			inputSlotSelectionState,
			clientFallbackStarter,
			this::showBookmarkPanel
		);
	}

	private static <R> @Nullable FocusedRecipe getFocusedRecipe(IRecipeLayoutDrawable<R> recipeLayoutDrawable) {
		ResourceLocation recipeUid = recipeLayoutDrawable.getRecipeCategory().getRegistryName(recipeLayoutDrawable.getRecipe());
		if (recipeUid == null) {
			return null;
		}
		return new FocusedRecipe(recipeLayoutDrawable.getRecipeCategory().getRecipeType().getUid(), recipeUid);
	}

	record RecipeLayoutForkExtras(
		RecipeFavoriteButton favoriteButton,
		InputSlotSelectionState inputSlotSelectionState,
		ClientFallbackStarter clientFallbackStarter,
		Runnable showBookmarkPanel
	) {
	}

	public void back() {
		navigate(RecipeNavigationDirection.BACK, Screen.hasShiftDown());
	}

	boolean navigate(RecipeNavigationDirection direction, boolean jumpToEnd) {
		captureCurrentInputSelections();
		if (navigationLogic.navigate(direction, jumpToEnd)) {
			restoreNavigationView();
			return true;
		}
		return false;
	}

	boolean canNavigate(RecipeNavigationDirection direction) {
		return navigationLogic.canNavigate(direction);
	}

	Optional<Component> getNavigationTargetTitle(RecipeNavigationDirection direction) {
		return navigationLogic.getNavigationTargetTitle(direction);
	}

	private void prepareForLookup() {
		if (isOpen()) {
			captureCurrentInputSelections();
		} else {
			navigationLogic.clearHistory();
		}
	}

	private void captureCurrentInputSelections() {
		navigationLogic.updateCurrentInputSelections(layouts.captureInputSelections());
	}

	private void restoreNavigationView() {
		filterSettings.restore(navigationLogic.getFilterMode(), navigationLogic.getSearchQueryText());
		recipeSearchField.setValue(filterSettings.getDraftQuery());
		recipeSearchField.setFocused(false);
		layouts.restoreInputSelections(navigationLogic.getCurrentInputSelections());
	}

	private boolean showAllRecipes() {
		captureCurrentInputSelections();
		return logic.showAllRecipes();
	}

	private void updateLayout() {
		if (!init) {
			return;
		}

		int titleControlsInset = nextRecipeCategory.getWidth() +
			backNavigation.getWidth() +
			forwardNavigation.getWidth() +
			(2 * topBarControlGap) +
			titleInnerPadding;
		ImmutableRect2i titleArea = MathUtil.union(previousRecipeCategory.getArea(), nextRecipeCategory.getArea())
			.cropLeft(titleControlsInset)
			.cropRight(titleControlsInset);
		IRecipeCategory<?> recipeCategory = logic.getSelectedRecipeCategory();
		this.recipeCategoryTitle = logic.hasRecipeResults() ?
			RecipeCategoryTitle.create(recipeCategory, font, titleArea) :
			new RecipeCategoryTitle();

		ImmutableRect2i recipeLayoutsArea = getRecipeLayoutsArea();
		final int availableHeight = recipeLayoutsArea.getHeight();

		AbstractContainerMenu containerMenu = getParentContainerMenu();
		List<IRecipeLayoutWithButtons<?>> recipeLayoutsWithButtons = logic.getVisibleRecipeLayoutsWithButtons(
			availableHeight,
			minRecipePadding,
			containerMenu,
			bookmarks,
			this
		);
		int recipesPerPage = this.logic.getRecipesPerPage();

		this.layouts.setRecipeLayoutsWithButtons(recipeLayoutsWithButtons);
		this.layouts.tick(containerMenu);
		this.area = calculateAreaToFitLayouts(this.idealArea, this.width, this.layouts.getWidth());
		recipeLayoutsArea = getRecipeLayoutsArea();

		this.layouts.updateLayout(recipeLayoutsArea, recipesPerPage);

		this.nextRecipeCategory.tick();
		this.previousRecipeCategory.tick();
		this.backNavigation.tick();
		this.forwardNavigation.tick();
		this.nextPage.tick();
		this.previousPage.tick();

		pageString = logic.getPageString();

		optionButtons.updateLayout(this.area);
		ImmutableRect2i optionButtonsArea = optionButtons.getArea();
		List<ITypedIngredient<?>> recipeCatalystIngredients = logic.getRecipeCatalysts().toList();
		recipeCatalysts.updateLayout(recipeCatalystIngredients, this.area, optionButtonsArea);
		recipeGuiTabs.initLayout(this.idealArea);
		updateRecipeFilterLayout();
	}

	private void updateRecipeFilterLayout() {
		ImmutableRect2i controlsArea = recipeGuiTabs.getTopBarControlsArea()
			.cropLeft(topBarControlGap);
		ImmutableRect2i filterButtonArea = controlsArea.keepLeft(filterModeButtonWidth);
		filterModeButton.updateBounds(filterButtonArea);
		ImmutableRect2i searchArea = controlsArea.cropLeft(filterModeButtonWidth + topBarControlGap);
		recipeSearchField.updateBounds(searchArea);
	}

	private void applyRecipeResultFilter() {
		captureCurrentInputSelections();
		logic.applyRecipeResultFilter(filterSettings.getMode(), filterSettings.getAppliedQuery());
		layouts.restoreInputSelections(navigationLogic.getCurrentInputSelections());
	}

	private ImmutableRect2i getRecipeLayoutsArea() {
		return new ImmutableRect2i(
			area.getX() + borderPadding,
			area.getY() + headerHeight + navBarPadding,
			area.getWidth() - (2 * borderPadding),
			area.getHeight() - (headerHeight + borderPadding + navBarPadding)
		);
	}

	@Nullable
	public AbstractContainerMenu getParentContainerMenu() {
		Screen screen;
		if (parentScreen == null) {
			screen = Minecraft.getInstance().screen;
		} else {
			screen = parentScreen;
		}
		if (screen instanceof AbstractContainerScreen<?> containerScreen) {
			return containerScreen.getMenu();
		}
		return null;
	}

	@Override
	public Optional<Screen> getParentScreen() {
		return Optional.ofNullable(parentScreen);
	}

	@Nullable
	public IGuiProperties getProperties() {
		if (width <= 0 || height <= 0) {
			return null;
		}
		int extraWidth = getLeftSideExtraWidth();
		ImmutableRect2i recipeArea = getArea();
		int guiXSize = recipeArea.getWidth() + extraWidth;
		int guiYSize = recipeArea.getHeight();
		if (guiXSize <= 0 || guiYSize <= 0) {
			return null;
		}
		return new GuiProperties(
			getClass(),
			recipeArea.getX() - extraWidth,
			recipeArea.getY(),
			guiXSize,
			guiYSize,
			width,
			height
		);
	}

	private static class UserInputHandler implements IUserInputHandler {
		private final RecipesGui recipesGui;

		public UserInputHandler(RecipesGui recipesGui) {
			this.recipesGui = recipesGui;
		}

		@Override
		public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
			double mouseX = input.getMouseX();
			double mouseY = input.getMouseY();
			if (recipesGui.isMouseOver(mouseX, mouseY)) {
				if (recipesGui.recipeCategoryTitle.isMouseOver(mouseX, mouseY)) {
					if (input.is(keyBindings.getLeftClick()))
						if (input.isSimulate() || recipesGui.showAllRecipes()) {
							return Optional.of(this);
						}
				}
			}

			Optional<RecipeNavigationKeyInput.Action> navigationAction = RecipeNavigationKeyInput.getAction(input);
			if (navigationAction.isPresent()) {
				if (!input.isSimulate()) {
					RecipeNavigationKeyInput.Action action = navigationAction.get();
					recipesGui.navigate(action.direction(), action.jumpToEnd());
				}
				return Optional.of(this);
			}

			Minecraft minecraft = Minecraft.getInstance();
			if (input.is(keyBindings.getCloseRecipeGui()) || input.is(minecraft.options.keyInventory)) {
				if (!input.isSimulate()) {
					recipesGui.onClose();
				}
				return Optional.of(this);
			} else if (input.is(keyBindings.getRecipeBack())) {
				if (!input.isSimulate()) {
					recipesGui.back();
				}
				return Optional.of(this);
			} else if (input.is(keyBindings.getNextCategory())) {
				if (!input.isSimulate()) {
					recipesGui.logic.nextRecipeCategory();
				}
				return Optional.of(this);
			} else if (input.is(keyBindings.getPreviousCategory())) {
				if (!input.isSimulate()) {
					recipesGui.logic.previousRecipeCategory();
				}
				return Optional.of(this);
			} else if (input.is(keyBindings.getNextRecipePage())) {
				if (!input.isSimulate()) {
					recipesGui.logic.nextPage();
				}
				return Optional.of(this);
			} else if (input.is(keyBindings.getPreviousRecipePage())) {
				if (!input.isSimulate()) {
					recipesGui.logic.previousPage();
				}
				return Optional.of(this);
			}

			return Optional.empty();
		}

		@Override
		public Optional<IUserInputHandler> handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
			if (recipesGui.isMouseOver(mouseX, mouseY)) {
				if (Screen.hasShiftDown()) {
					if (scrollDeltaY < 0) {
						recipesGui.logic.nextRecipeCategory();
						return Optional.of(this);
					} else if (scrollDeltaY > 0) {
						recipesGui.logic.previousRecipeCategory();
						return Optional.of(this);
					}
				} else {
					if (scrollDeltaY < 0) {
						recipesGui.logic.nextPage();
						return Optional.of(this);
					} else if (scrollDeltaY > 0) {
						recipesGui.logic.previousPage();
						return Optional.of(this);
					}
				}
			}

			return Optional.empty();
		}
	}
}

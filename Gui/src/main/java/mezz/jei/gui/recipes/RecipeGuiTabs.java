package mezz.jei.gui.recipes;

import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.handlers.InputGroup;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.PageNavigation;
import mezz.jei.gui.input.IPaged;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The area drawn on top and bottom of the {@link RecipesGui} that show the recipe categories.
 */
public class RecipeGuiTabs implements IPaged, IInputTarget {
	private static final int TAB_GUI_OVERLAP = 3;
	private static final int TAB_HORIZONTAL_INSET = 2;
	private static final int NAVIGATION_HEIGHT = 20;
	private static final int NAVIGATION_WIDTH = 72;

	private final IRecipeGuiLogic recipeGuiLogic;
	private final List<RecipeGuiTab> tabs = new ArrayList<>();
	private final PageNavigation pageNavigation;
	private final IRecipeManager recipeManager;
	private final IGuiHelper guiHelper;
	private IInputTarget inputHandler;
	private ImmutableRect2i area = ImmutableRect2i.EMPTY;
	private ImmutableRect2i topBarArea = ImmutableRect2i.EMPTY;

	private int pageCount = 1;
	private int pageNumber = 0;
	private int categoriesPerPage = 1;

	public RecipeGuiTabs(IRecipeGuiLogic recipeGuiLogic, IRecipeManager recipeManager, IGuiHelper guiHelper) {
		this.recipeGuiLogic = recipeGuiLogic;
		this.pageNavigation = new PageNavigation(this, false);
		this.recipeManager = recipeManager;
		this.guiHelper = guiHelper;
		this.inputHandler = this.pageNavigation.createInputHandler();
	}

	public void initLayout(ImmutableRect2i recipeGuiArea) {
		List<IRecipeCategory<?>> categories = this.recipeGuiLogic.getRecipeCategories();
		final ImmutableRect2i tabsArea = recipeGuiArea
			.keepTop(RecipeGuiTab.TAB_HEIGHT)
			// move up above the recipe area and overlap the recipe gui
			.moveUp(RecipeGuiTab.TAB_HEIGHT - TAB_GUI_OVERLAP)
			// inset to avoid the recipe gui corners
			.cropLeft(TAB_HORIZONTAL_INSET)
			.cropRight(TAB_HORIZONTAL_INSET);
		this.topBarArea = tabsArea
			.keepTop(NAVIGATION_HEIGHT)
			.moveUp(2 + NAVIGATION_HEIGHT);
		pageNavigation.updateBounds(topBarArea.keepLeft(NAVIGATION_WIDTH));

		if (categories.isEmpty()) {
			this.area = ImmutableRect2i.EMPTY;
			this.pageCount = 1;
			this.pageNumber = 0;
			this.categoriesPerPage = 1;
			updateLayout();
			return;
		}

		categoriesPerPage = Math.min(tabsArea.getWidth() / RecipeGuiTab.TAB_WIDTH, categories.size());
		final int tabsWidth = categoriesPerPage * RecipeGuiTab.TAB_WIDTH;

		this.area = tabsArea.keepLeft(tabsWidth);

		pageCount = MathUtil.divideCeil(categories.size(), categoriesPerPage);

		IRecipeCategory<?> currentCategory = recipeGuiLogic.getSelectedRecipeCategory();
		int categoryIndex = categories.indexOf(currentCategory);
		pageNumber = categoryIndex / categoriesPerPage;

		updateLayout();
	}

	public ImmutableRect2i getTopBarArea() {
		return topBarArea;
	}

	public ImmutableRect2i getTopBarControlsArea() {
		return topBarArea.cropLeft(NAVIGATION_WIDTH);
	}

	private void updateLayout() {
		tabs.clear();
		List<IInputTarget> inputHandlers = new ArrayList<>();

		List<IRecipeCategory<?>> categories = recipeGuiLogic.getRecipeCategories();

		int tabX = area.getX();

		final int startIndex = pageNumber * categoriesPerPage;
		for (int i = 0; i < categoriesPerPage; i++) {
			int index = i + startIndex;
			if (index >= categories.size()) {
				break;
			}
			IRecipeCategory<?> category = categories.get(index);
			RecipeGuiTab tab = new RecipeCategoryTab(
				recipeGuiLogic,
				category,
				tabX,
				area.getY(),
				recipeManager,
				guiHelper
			);
			this.tabs.add(tab);
			inputHandlers.add(tab);
			tabX += RecipeGuiTab.TAB_WIDTH;
		}

		inputHandlers.add(this.pageNavigation.createInputHandler());
		this.inputHandler = new InputGroup("RecipeGuiTabs", inputHandlers);

		pageNavigation.updatePageNumber();
	}

	public void draw(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		IRecipeCategory<?> selectedCategory = recipeGuiLogic.getSelectedRecipeCategory();

		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		RecipeGuiTab hovered = null;

		RenderSystem.disableDepthTest();
		{
			for (RecipeGuiTab tab : tabs) {
				boolean selected = tab.isSelected(selectedCategory);
				tab.draw(selected, guiGraphics, mouseX, mouseY);
				if (tab.isMouseOver(mouseX, mouseY)) {
					hovered = tab;
				}
			}
		}
		RenderSystem.enableDepthTest();

		pageNavigation.draw(minecraft, guiGraphics, mouseX, mouseY, partialTicks);

		if (hovered != null) {
			JeiTooltip tooltip = hovered.getTooltip();
			tooltip.draw(guiGraphics, mouseX, mouseY);
		}
	}

	@Override
	public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keys) {
		return inputHandler.beginInput(screen, input, keys);
	}

	@Override
	public boolean scroll(double mouseX, double mouseY, double scrollX, double scrollY) {
		return inputHandler.scroll(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void resetInput() {
		inputHandler.resetInput();
	}

	public boolean isMouseOver(double mouseX, double mouseY) {
		return area.contains(mouseX, mouseY) || pageNavigation.isMouseOver(mouseX, mouseY);
	}

	public java.util.Optional<IRecipeCategory<?>> getCategoryUnderMouse(double x, double y) {
		for (int i = 0; i < tabs.size(); i++) {
			if (tabs.get(i).isMouseOver(x, y))
				return java.util.Optional.of(recipeGuiLogic.getRecipeCategories().get(pageNumber * categoriesPerPage + i));
		}
		return java.util.Optional.empty();
	}

	@Override
	public boolean nextPage() {
		if (hasNext()) {
			pageNumber++;
		} else {
			pageNumber = 0;
		}
		updateLayout();
		return true;
	}

	@Override
	public boolean hasNext() {
		return pageNumber + 1 < pageCount;
	}

	@Override
	public boolean previousPage() {
		if (hasPrevious()) {
			pageNumber--;
		} else {
			pageNumber = pageCount - 1;
		}
		updateLayout();
		return true;
	}

	@Override
	public boolean hasPrevious() {
		return pageNumber > 0;
	}

	@Override
	public int getPageCount() {
		return pageCount;
	}

	@Override
	public int getPageNumber() {
		return pageNumber;
	}
}

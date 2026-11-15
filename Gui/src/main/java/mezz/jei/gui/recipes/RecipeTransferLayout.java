package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Supplies selected inputs without moving the upstream transfer handler invocation. */
record RecipeTransferLayout<R>(IRecipeLayoutDrawable<R> delegate, Supplier<IRecipeSlotsView> slots) implements IRecipeLayoutDrawable<R> {
	@Override
	public IRecipeSlotsView getRecipeSlotsView() {
		return slots.get();
	}

	@Override
	public void setPosition(int posX, int posY) {
		delegate.setPosition(posX, posY);
	}

	@Override
	public void drawRecipe(GuiGraphics graphics, int mouseX, int mouseY) {
		delegate.drawRecipe(graphics, mouseX, mouseY);
	}

	@Override
	public void drawOverlays(GuiGraphics graphics, int mouseX, int mouseY) {
		delegate.drawOverlays(graphics, mouseX, mouseY);
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return delegate.isMouseOver(mouseX, mouseY);
	}

	@Override
	public <T> Optional<T> getIngredientUnderMouse(int mouseX, int mouseY, IIngredientType<T> ingredientType) {
		return delegate.getIngredientUnderMouse(mouseX, mouseY, ingredientType);
	}

	@Override
	@SuppressWarnings("deprecation")
	public Optional<IRecipeSlotDrawable> getRecipeSlotUnderMouse(double mouseX, double mouseY) {
		return delegate.getRecipeSlotUnderMouse(mouseX, mouseY);
	}

	@Override
	public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
		return delegate.getSlotUnderMouse(mouseX, mouseY);
	}

	@Override
	public Rect2i getRect() {
		return delegate.getRect();
	}

	@Override
	public Rect2i getRectWithBorder() {
		return delegate.getRectWithBorder();
	}

	@Override
	public Rect2i getSideButtonArea(int buttonIndex) {
		return delegate.getSideButtonArea(buttonIndex);
	}

	@Override
	public IRecipeCategory<R> getRecipeCategory() {
		return delegate.getRecipeCategory();
	}

	@Override
	public R getRecipe() {
		return delegate.getRecipe();
	}

	@Override
	public List<ResourceLocation> getSourceRecipeIds() {
		return delegate.getSourceRecipeIds();
	}

	@Override
	public IJeiInputHandler getInputHandler() {
		return delegate.getInputHandler();
	}

	@Override
	public void tick() {
		delegate.tick();
	}
}

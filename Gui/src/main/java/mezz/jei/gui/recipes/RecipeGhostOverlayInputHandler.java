package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayActivator;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

public class RecipeGhostOverlayInputHandler implements IUserInputHandler {
	@FunctionalInterface
	public interface Activator {
		boolean activate(UserInput input, IRecipeLayoutDrawable<?> recipeLayout, @Nullable AbstractContainerMenu containerMenu, Runnable onActivated);
	}

	private final IRecipeLayoutDrawable<?> recipeLayout;
	private final BiPredicate<Double, Double> isMouseOver;
	private final Function<Screen, @Nullable AbstractContainerMenu> parentContainerMenu;
	private final Consumer<Screen> onActivated;
	private final Activator activator;

	public RecipeGhostOverlayInputHandler(IRecipeLayoutDrawable<?> recipeLayout, BiPredicate<Double, Double> isMouseOver) {
		this(
			recipeLayout,
			isMouseOver,
			BookmarkGhostOverlayActivator::getCurrentOrParentContainerMenu,
			BookmarkGhostOverlayActivator::closeRecipeGui,
			BookmarkGhostOverlayActivator::activate
		);
	}

	public RecipeGhostOverlayInputHandler(
		IRecipeLayoutDrawable<?> recipeLayout,
		BiPredicate<Double, Double> isMouseOver,
		Function<Screen, @Nullable AbstractContainerMenu> parentContainerMenu,
		Consumer<Screen> onActivated,
		Activator activator
	) {
		this.recipeLayout = recipeLayout;
		this.isMouseOver = isMouseOver;
		this.parentContainerMenu = parentContainerMenu;
		this.onActivated = onActivated;
		this.activator = activator;
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		double mouseX = input.getMouseX();
		double mouseY = input.getMouseY();
		if (!BookmarkGhostOverlayActivator.isOverlayRecipeInput(input, keyBindings.getOverlayRecipe()) || !isMouseOver.test(mouseX, mouseY)) {
			return Optional.empty();
		}

		AbstractContainerMenu containerMenu = parentContainerMenu.apply(screen);
		if (activator.activate(input, recipeLayout, containerMenu, () -> onActivated.accept(screen))) {
			return Optional.of(this);
		}
		return Optional.empty();
	}
}

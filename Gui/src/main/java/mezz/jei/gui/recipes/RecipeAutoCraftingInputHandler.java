package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.common.Internal;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator.ClientFallbackStarter;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayActivator;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class RecipeAutoCraftingInputHandler implements IUserInputHandler {
	@FunctionalInterface
	public interface Activator {
		boolean activate(UserInput input, IRecipeLayoutDrawable<?> recipeLayout, @Nullable AbstractContainerMenu containerMenu, Runnable onActivated);
	}

	private final IRecipeLayoutDrawable<?> recipeLayout;
	private final Function<Screen, @Nullable AbstractContainerMenu> parentContainerMenu;
	private final Consumer<Screen> onActivated;
	private final Activator activator;

	public RecipeAutoCraftingInputHandler(IRecipeLayoutDrawable<?> recipeLayout) {
		this(recipeLayout, ClientFallbackStarter.DISABLED);
	}

	public RecipeAutoCraftingInputHandler(IRecipeLayoutDrawable<?> recipeLayout, ClientFallbackStarter clientFallbackStarter) {
		this(
			recipeLayout,
			BookmarkGhostOverlayActivator::getCurrentOrParentContainerMenu,
			BookmarkGhostOverlayActivator::closeRecipeGui,
			(input, layout, menu, onActivated) -> {
				IConnectionToServer serverConnection = Internal.getServerConnection();
				return BookmarkAutoCraftingActivator.activate(
					input,
					layout,
					menu,
					onActivated,
					(input.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0,
					JeiClientSoundUtil::playClickSound,
					serverConnection::sendPacketToServer,
					serverConnection.isJeiOnServer(),
					BookmarkAutoCraftingActivator.getPlayerInventoryStacks(),
					clientFallbackStarter
				);
			}
		);
	}

	public RecipeAutoCraftingInputHandler(
		IRecipeLayoutDrawable<?> recipeLayout,
		Function<Screen, @Nullable AbstractContainerMenu> parentContainerMenu,
		Consumer<Screen> onActivated,
		Activator activator
	) {
		this.recipeLayout = recipeLayout;
		this.parentContainerMenu = parentContainerMenu;
		this.onActivated = onActivated;
		this.activator = activator;
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		double mouseX = input.getMouseX();
		double mouseY = input.getMouseY();
		if (!BookmarkAutoCraftingActivator.isAutoCraftingInput(input, keyBindings.getCraftItems()) ||
			!recipeLayout.isMouseOver(mouseX, mouseY) ||
			!isOutputSlotUnderMouse(mouseX, mouseY)) {
			return Optional.empty();
		}
		if (!BookmarkAutoCraftingActivator.claimAutoCraftingInput(input, keyBindings.getCraftItems())) {
			return Optional.empty();
		}

		AbstractContainerMenu containerMenu = parentContainerMenu.apply(screen);
		if (activator.activate(input, recipeLayout, containerMenu, () -> onActivated.accept(screen))) {
			return Optional.of(this);
		}
		BookmarkAutoCraftingActivator.releaseAutoCraftingInput(input.getKey());
		return Optional.empty();
	}

	private boolean isOutputSlotUnderMouse(double mouseX, double mouseY) {
		return recipeLayout.getSlotUnderMouse(mouseX, mouseY)
			.map(RecipeSlotUnderMouse::slot)
			.map(slot -> slot.getRole() == mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT)
			.orElse(false);
	}
}

package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.Internal;
import mezz.jei.common.config.DebugConfig;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator.ClientFallbackStarter;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayActivator;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class RecipeAutoCraftingInputHandler implements IUserInputHandler {
	private static final Logger LOGGER = LogManager.getLogger();
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
		boolean isCraftKey = BookmarkAutoCraftingActivator.isAutoCraftingInput(input, keyBindings.getCraftItems());
		boolean mouseOver = recipeLayout.isMouseOver(mouseX, mouseY);
		boolean outputUnderMouse = isOutputSlotUnderMouse(mouseX, mouseY);
		if (!isCraftKey || !mouseOver || !outputUnderMouse) {
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] INPUT-REJECT screen={} craftKey={} mouseOver={} outputUnderMouse={}",
					screen.getClass().getSimpleName(), isCraftKey, mouseOver, outputUnderMouse);
			}
			return Optional.empty();
		}
		if (!BookmarkAutoCraftingActivator.claimAutoCraftingInput(input, keyBindings.getCraftItems())) {
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] INPUT-CLAIM-FAIL screen={}", screen.getClass().getSimpleName());
			}
			return Optional.empty();
		}

		@Nullable AbstractContainerMenu containerMenu = parentContainerMenu.apply(screen);
		if (activator.activate(input, recipeLayout, containerMenu, () -> onActivated.accept(screen))) {
			return Optional.of(this);
		}
		if (DebugConfig.isDebugModeEnabled()) {
			LOGGER.info("[Bug6] INPUT-ACTIVATOR-FAIL screen={} menu={}",
				screen.getClass().getSimpleName(),
				containerMenu == null ? "null" : containerMenu.getClass().getSimpleName());
		}
		BookmarkAutoCraftingActivator.releaseAutoCraftingInput(input.getKey());
		return Optional.empty();
	}

	private boolean isOutputSlotUnderMouse(double mouseX, double mouseY) {
		return recipeLayout.getSlotUnderMouse(mouseX, mouseY)
			.map(RecipeSlotUnderMouse::slot)
			.map(slot -> slot.getRole() == RecipeIngredientRole.OUTPUT)
			.orElse(false);
	}
}

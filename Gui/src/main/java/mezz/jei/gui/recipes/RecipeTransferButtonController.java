package mezz.jei.gui.recipes;

import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.JeiGuiColors;
import mezz.jei.common.gui.JeiGuiColors.GuiColor;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.transfer.RecipeTransferErrorInternal;
import mezz.jei.common.transfer.RecipeTransferService;
import mezz.jei.common.transfer.RecipeTransferUtil;
import mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridgeRegistry;
import mezz.jei.gui.bookmarks.tree.RecipeTreeScreen;

import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class RecipeTransferButtonController implements IIconButtonController {
	private final IRecipeLayoutDrawable<?> recipeLayout;
	private @Nullable RecipesGui recipesGui;
	private @Nullable InputSlotSelectionState inputSlotSelectionState;
	private final RecipeTransferService recipeTransferService;
	private final Supplier<@Nullable AbstractContainerScreen<?>> screenSupplier;
	private final BooleanSupplier maxTransferSupplier;
	private final Runnable onSuccessfulTransfer;
	private @Nullable IRecipeTransferError recipeTransferError;

	public RecipeTransferButtonController(
		IRecipeLayoutDrawable<?> recipeLayout,
		RecipesGui recipesGui,
		RecipeTransferService recipeTransferService
	) {
		this(
			recipeLayout,
			recipeTransferService,
			recipesGui::getParentContainerScreen,
			Screen::hasShiftDown,
			recipesGui::onClose
		);
		this.recipesGui = recipesGui;
	}

	RecipeTransferButtonController(
		IRecipeLayoutDrawable<?> recipeLayout,
		RecipesGui recipesGui,
		@Nullable InputSlotSelectionState inputSlotSelectionState,
		RecipeTransferService recipeTransferService
	) {
		this(recipeLayout, recipesGui, recipeTransferService);
		this.recipesGui = recipesGui;
		this.inputSlotSelectionState = inputSlotSelectionState;
	}

	public static RecipeTransferButtonController createForPinnedRecipe(
		IRecipeLayoutDrawable<?> recipeLayout,
		RecipeTransferService recipeTransferService
	) {
		PinnedTransferState transferState = new PinnedTransferState();
		return new RecipeTransferButtonController(
			recipeLayout,
			recipeTransferService,
			RecipeTransferButtonController::getCurrentContainerScreen,
			transferState::shouldTransferMax,
			transferState::onSuccessfulTransfer
		);
	}

	private RecipeTransferButtonController(
		IRecipeLayoutDrawable<?> recipeLayout,
		RecipeTransferService recipeTransferService,
		Supplier<@Nullable AbstractContainerScreen<?>> screenSupplier,
		BooleanSupplier maxTransferSupplier,
		Runnable onSuccessfulTransfer
	) {
		this.recipeLayout = recipeLayout;
		this.recipeTransferService = recipeTransferService;
		this.screenSupplier = screenSupplier;
		this.maxTransferSupplier = maxTransferSupplier;
		this.onSuccessfulTransfer = onSuccessfulTransfer;
	}

	private static @Nullable AbstractContainerScreen<?> getCurrentContainerScreen() {
		Screen screen = Minecraft.getInstance().screen;
		if (screen instanceof AbstractContainerScreen<?> containerScreen) {
			return containerScreen;
		}
		return null;
	}

	private static class PinnedTransferState {
		private boolean transferred;

		public boolean shouldTransferMax() {
			return transferred;
		}

		public void onSuccessfulTransfer() {
			this.transferred = true;
		}
	}

	@Override
	public void initState(IButtonState state) {
		Textures textures = Internal.getTextures();
		state.setIcon(textures.getRecipeTransfer());
		updateState(state);
	}

	@Override
	public void updateState(IButtonState state) {
		if (treeTarget().isPresent()) {
			recipeTransferError = null;
			state.setActive(true);
			state.setVisible(true);
			return;
		}
		Player player = Minecraft.getInstance().player;
		AbstractContainerScreen<?> parentScreen = screenSupplier.get();
		if (parentScreen != null && player != null) {
			this.recipeTransferError = recipeTransferService.getTransferRecipeErrorWithSlotsView(parentScreen, recipeLayout, getRecipeSlotsView(), player)
				.orElse(null);
		} else {
			this.recipeTransferError = RecipeTransferErrorInternal.INSTANCE;
		}

		updateStateForTransferError(state, recipeTransferError);
	}

	static void updateStateForTransferError(IButtonState state, @Nullable IRecipeTransferError recipeTransferError) {
		if (recipeTransferError == null ||
			recipeTransferError.getType().allowsTransfer
		) {
			state.setActive(true);
			state.setVisible(true);
		} else {
			state.setActive(false);
			IRecipeTransferError.Type type = recipeTransferError.getType();
			state.setVisible(type == IRecipeTransferError.Type.USER_FACING);
		}
	}

	@Override
	public boolean onPress(IJeiUserInput input) {
		var tree = treeTarget();
		if (tree.isPresent()) {
			if (!input.isSimulate() && tree.get().addRecipe(recipeLayout,
				inputSlotSelectionState == null ? Map.of() : inputSlotSelectionState.selectedKeys(),
				inputSlotSelectionState == null ? Map.of() : inputSlotSelectionState.filteredCandidates())
			) {
				recipesGui.onClose();
			}
			return true;
		}
		if (!input.isSimulate()) {
			boolean maxTransfer = maxTransferSupplier.getAsBoolean();
			Minecraft minecraft = Minecraft.getInstance();
			LocalPlayer player = minecraft.player;
			AbstractContainerScreen<?> parentScreen = screenSupplier.get();
			if (parentScreen != null && player != null) {
				IRecipeSlotsView recipeSlotsView = getRecipeSlotsView();
				boolean transferred = inputSlotSelectionState != null &&
					inputSlotSelectionState.hasSelections() &&
					Ae2RecipeChainPatternEncodingBridgeRegistry.getBridge()
						.transferSelectedCraftingRecipe(
							parentScreen.getMenu(),
							recipeLayout.getRecipe(),
							recipeLayout.getRecipeCategory().getRecipeType(),
							recipeSlotsView
						);
				if (!transferred) {
					transferred = recipeTransferService.transferRecipeWithSlotsView(
						parentScreen,
						recipeLayout,
						recipeSlotsView,
						player,
						maxTransfer
					);
				}
				if (transferred) {
					onSuccessfulTransfer.run();
				}
			}
		}
		return true;
	}

	@Override
	public void getTooltips(ITooltipBuilder tooltip) {
		if (treeTarget().isPresent()) {
			tooltip.add(Component.translatable("jei.tree.add_recipe"));
			return;
		}
		getTooltips(this.recipeTransferError, tooltip);
	}

	private Optional<RecipeTreeScreen> treeTarget() {
		return Optional.ofNullable(recipesGui).flatMap(RecipesGui::getParentScreen).filter(RecipeTreeScreen.class::isInstance).map(RecipeTreeScreen.class::cast);
	}

	static void getTooltips(@Nullable IRecipeTransferError recipeTransferError, ITooltipBuilder tooltip) {
		RecipeTransferUtil.addTransferRecipeTooltip(recipeTransferError, tooltip);
	}

	@Override
	public void drawExtras(GuiGraphics guiGraphics, Rect2i buttonArea, int mouseX, int mouseY, float partialTicks) {
		IRecipeTransferError recipeTransferError = this.recipeTransferError;
		if (recipeTransferError != null) {
			if (recipeTransferError.getType() == IRecipeTransferError.Type.COSMETIC) {
				int color = recipeTransferError.getButtonHighlightColor();
				if (color == IRecipeTransferError.DEFAULT_BUTTON_HIGHLIGHT_COLOR) {
					color = JeiGuiColors.getColor(GuiColor.RECIPE_TRANSFER_BUTTON_HIGHLIGHT);
				}
				guiGraphics.fill(
					RenderType.guiOverlay(),
					buttonArea.getX(),
					buttonArea.getY(),
					buttonArea.getX() + buttonArea.getWidth(),
					buttonArea.getY() + buttonArea.getHeight(),
					color
				);
			}
			if (buttonArea.contains(mouseX, mouseY)) {
				IRecipeSlotsView recipeSlotsView = getRecipeSlotsView();
				Rect2i recipeRect = recipeLayout.getRect();
				PoseStack poseStack = guiGraphics.pose();
				runWithRestoredPose(poseStack, () -> recipeTransferError.showError(guiGraphics, mouseX, mouseY, recipeSlotsView, recipeRect.getX(), recipeRect.getY()));
			}
		}
	}

	static void runWithRestoredPose(PoseStack poseStack, Runnable action) {
		poseStack.pushPose();
		try {
			action.run();
		} finally {
			poseStack.popPose();
		}
	}

	public int getMissingCountHint() {
		return getMissingCountHint(this.recipeTransferError);
	}

	IRecipeSlotsView getRecipeSlotsView() {
		if (inputSlotSelectionState == null) {
			return recipeLayout.getRecipeSlotsView();
		}
		return inputSlotSelectionState.createTransferSlotsView(recipeLayout);
	}

	static int getMissingCountHint(@Nullable IRecipeTransferError recipeTransferError) {
		if (recipeTransferError == null) {
			return 0;
		}
		return recipeTransferError.getMissingCountHint();
	}
}

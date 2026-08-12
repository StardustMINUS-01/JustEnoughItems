package mezz.jei.gui.bookmarks.hotkeys;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.Internal;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.network.packets.PacketFillCraftingGrid;
import mezz.jei.common.network.packets.PlayToServerPacket;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

public final class BookmarkGhostOverlayActivator {
	private BookmarkGhostOverlayActivator() {
	}

	public static boolean isOverlayRecipeInput(UserInput input, IJeiKeyMapping overlayRecipeKey) {
		if (input.is(overlayRecipeKey)) {
			return true;
		}
		return InputModifiers.hasShift(input) && overlayRecipeKey.matchesIgnoringModifiers(input.getKey());
	}

	public static boolean activate(
		UserInput input,
		IRecipeLayoutDrawable<?> recipeLayout,
		@Nullable AbstractContainerMenu containerMenu,
		Runnable onActivated
	) {
		return activate(input, recipeLayout, containerMenu, onActivated, OptionalInt.empty());
	}

	public static boolean activate(
		UserInput input,
		IRecipeLayoutDrawable<?> recipeLayout,
		@Nullable AbstractContainerMenu containerMenu,
		Runnable onActivated,
		OptionalInt bookmarkQuantity
	) {
		IConnectionToServer serverConnection = Internal.getServerConnection();
		return activate(
			input,
			recipeLayout,
			containerMenu,
			onActivated,
			bookmarkQuantity,
			InputModifiers.hasShift(input),
			InputModifiers.hasControl(input),
			JeiClientSoundUtil::playClickSound,
			serverConnection::sendPacketToServer,
			serverConnection.isJeiOnServer()
		);
	}

	public static boolean activate(
		UserInput input,
		IRecipeLayoutDrawable<?> recipeLayout,
		AbstractContainerScreen<?> containerScreen,
		Runnable onActivated,
		OptionalInt bookmarkQuantity
	) {
		IConnectionToServer serverConnection = Internal.getServerConnection();
		return activate(
			input,
			recipeLayout,
			containerScreen,
			onActivated,
			bookmarkQuantity,
			InputModifiers.hasShift(input),
			InputModifiers.hasControl(input),
			JeiClientSoundUtil::playClickSound,
			serverConnection::sendPacketToServer,
			serverConnection.isJeiOnServer()
		);
	}

	public static boolean activate(
		UserInput input,
		IRecipeLayoutDrawable<?> recipeLayout,
		@Nullable AbstractContainerMenu containerMenu,
		Runnable onActivated,
		boolean hasShift,
		Runnable playClickSound
	) {
		return activate(input, recipeLayout, containerMenu, onActivated, OptionalInt.empty(), hasShift, false, playClickSound, packet -> {}, false);
	}

	public static boolean activate(
		UserInput input,
		IRecipeLayoutDrawable<?> recipeLayout,
		@Nullable AbstractContainerMenu containerMenu,
		Runnable onActivated,
		OptionalInt bookmarkQuantity,
		boolean hasShift,
		boolean hasControl,
		Runnable playClickSound,
		Consumer<PlayToServerPacket> packetSender,
		boolean hasServerSupport
	) {
		if (containerMenu == null) {
			return false;
		}
		return activate(
			input,
			recipeLayout,
			BookmarkGhostOverlayTargetSlots.fromMenu(containerMenu),
			containerMenu,
			onActivated,
			bookmarkQuantity,
			hasShift,
			hasControl,
			playClickSound,
			packetSender,
			hasServerSupport
		);
	}

	public static boolean activate(
		UserInput input,
		IRecipeLayoutDrawable<?> recipeLayout,
		AbstractContainerScreen<?> containerScreen,
		Runnable onActivated,
		OptionalInt bookmarkQuantity,
		boolean hasShift,
		boolean hasControl,
		Runnable playClickSound,
		Consumer<PlayToServerPacket> packetSender,
		boolean hasServerSupport
	) {
		if (containerScreen == null) {
			return false;
		}
		return activate(
			input,
			recipeLayout,
			BookmarkGhostOverlayTargetSlots.fromScreen(containerScreen),
			containerScreen.getMenu(),
			onActivated,
			bookmarkQuantity,
			hasShift,
			hasControl,
			playClickSound,
			packetSender,
			hasServerSupport
		);
	}

	private static boolean activate(
		UserInput input,
		IRecipeLayoutDrawable<?> recipeLayout,
		List<BookmarkGhostOverlay.TargetSlot> targetSlots,
		AbstractContainerMenu containerMenu,
		Runnable onActivated,
		OptionalInt bookmarkQuantity,
		boolean hasShift,
		boolean hasControl,
		Runnable playClickSound,
		Consumer<PlayToServerPacket> packetSender,
		boolean hasServerSupport
	) {
		if (targetSlots.isEmpty()) {
			return false;
		}

		if (hasShift) {
			return fillCraftingGrid(input, recipeLayout, containerMenu, onActivated, bookmarkQuantity, hasControl, playClickSound, packetSender, hasServerSupport, targetSlots.size());
		}

		BookmarkRecipeOverlayPlan plan = BookmarkRecipeOverlayPlan.fromHotkeyAction(
			BookmarkHotkeyAction.OVERLAY_RECIPE,
			OptionalInt.empty()
		).orElseThrow();
		Optional<BookmarkGhostOverlay> overlay = BookmarkGhostOverlay.create(plan, recipeLayout, targetSlots);
		if (overlay.isEmpty()) {
			return false;
		}

		if (!input.isSimulate()) {
			onActivated.run();
			BookmarkGhostOverlayState.INSTANCE.setActive(containerMenu, overlay.get());
			playClickSound.run();
		}
		return true;
	}

	private static boolean fillCraftingGrid(
		UserInput input,
		IRecipeLayoutDrawable<?> recipeLayout,
		AbstractContainerMenu containerMenu,
		Runnable onActivated,
		OptionalInt bookmarkQuantity,
		boolean hasControl,
		Runnable playClickSound,
		Consumer<PlayToServerPacket> packetSender,
		boolean hasServerSupport,
		int targetSlotCount
	) {
		if (!hasServerSupport) {
			return false;
		}

		BookmarkHotkeyAction action = hasControl && bookmarkQuantity.isPresent() ?
			BookmarkHotkeyAction.FILL_CRAFTING_GRID_QUANTITY :
			BookmarkHotkeyAction.FILL_CRAFTING_GRID;
		BookmarkRecipeOverlayPlan plan = BookmarkRecipeOverlayPlan.fromHotkeyAction(action, bookmarkQuantity)
			.orElseThrow();
		Optional<BookmarkCraftingGridFill> fill = BookmarkCraftingGridFill.create(
			recipeLayout,
			targetSlotCount,
			plan.multiplier().orElse(0)
		);
		if (fill.isEmpty()) {
			return false;
		}

		if (!input.isSimulate()) {
			onActivated.run();
			BookmarkCraftingGridFill request = fill.get();
			packetSender.accept(new PacketFillCraftingGrid(containerMenu.containerId, request.multiplier(), request.targetStacks()));
			playClickSound.run();
		}
		return true;
	}

	public static @Nullable AbstractContainerMenu getCurrentOrParentContainerMenu(Screen screen) {
		if (screen instanceof AbstractContainerScreen<?> containerScreen) {
			return containerScreen.getMenu();
		}
		if (screen instanceof RecipesGui recipesGui) {
			return recipesGui.getParentContainerMenu();
		}
		return null;
	}

	public static void closeRecipeGui(Screen screen) {
		if (screen instanceof RecipesGui recipesGui) {
			recipesGui.onClose();
		}
	}
}

package mezz.jei.forge.compat.tconstruct;

import mezz.jei.common.bookmarks.ICraftingGridCraftExecutor;
import mezz.jei.common.bookmarks.ServerBookmarkCraftingGridFill;
import mezz.jei.common.config.DebugConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Auto-crafting executor for Tinkers' Construct workstations (CraftingStation and TinkerStation).
 * Fills the grid, then shift-clicks the station's own result slot so the station's quickMoveStack
 * path (takeResult / onCraft) performs the actual crafting and inventory insertion.
 */
public class TinkerCraftingGridCraftExecutor implements ICraftingGridCraftExecutor {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int MAX_MULTIPLIER = 64;

	public static Optional<TinkerCraftingGridCraftExecutor> createIfLoaded() {
		return mezz.jei.forge.compat.CompatUtil.createIfLoaded(
			"slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu",
			TinkerCraftingGridCraftExecutor::new
		);
	}

	@Override
	public boolean canHandle(AbstractContainerMenu menu) {
		return TinkerCraftingGridAccess.find(menu).isPresent();
	}

	@Override
	public int craft(ServerPlayer player, int containerId, @Nullable ResourceLocation recipeId, List<ItemStack> targetStacks, int multiplier) {
		AbstractContainerMenu menu = player.containerMenu;
		Optional<TinkerCraftingGridAccess> access = TinkerCraftingGridAccess.find(menu);
		if (access.isEmpty() || menu.containerId != containerId || targetStacks.isEmpty()) {
			return 0;
		}

		int remaining = multiplier == 0 ? MAX_MULTIPLIER : Math.min(MAX_MULTIPLIER, Math.max(1, multiplier));
		int crafted = 0;
		while (remaining > 0) {
			int filled = ServerBookmarkCraftingGridFill.fill(
				menu,
				containerId,
				player.getInventory(),
				player,
				targetStacks,
				remaining,
				access.get().craftingSlots(),
				null
			);
			if (filled <= 0) {
				break;
			}
			menu.slotsChanged(player.getInventory());

			if (!takeResult(menu, access.get(), player)) {
				break;
			}
			crafted += filled;
			remaining -= filled;
		}

		if (crafted > 0) {
			player.getInventory().setChanged();
			menu.broadcastChanges();
		}
		if (DebugConfig.isDebugModeEnabled()) {
			LOGGER.info("[Bug6] COMPAT-TINKER-CRAFT containerId={} multiplier={} crafted={}", containerId, multiplier, crafted);
		}
		return crafted;
	}

	/**
	 * Shift-clicks the station's own result slot. Success is detected by the crafting inputs
	 * being consumed (both stations consume the grid inside quickMoveStack before moving the
	 * result), which is more reliable than matching the moved result stack (TinkerStation can
	 * rename/modify tools).
	 */
	private static boolean takeResult(AbstractContainerMenu menu, TinkerCraftingGridAccess access, ServerPlayer player) {
		Slot resultSlot = access.resultSlot();
		if (!resultSlot.hasItem() || !resultSlot.mayPickup(player)) {
			return false;
		}
		List<ItemStack> before = new ArrayList<>(access.craftingSlots().size());
		for (Slot slot : access.craftingSlots()) {
			before.add(slot.getItem().copy());
		}
		menu.clicked(access.resultSlotIndex(), 0, ClickType.QUICK_MOVE, player);
		boolean inputsConsumed = false;
		for (int i = 0; i < access.craftingSlots().size(); i++) {
			ItemStack after = access.craftingSlots().get(i).getItem();
			ItemStack prior = before.get(i);
			if (after.isEmpty() && !prior.isEmpty()) {
				inputsConsumed = true;
				break;
			}
			if (!after.isEmpty() && !prior.isEmpty() && after.getCount() < prior.getCount()) {
				inputsConsumed = true;
				break;
			}
		}
		return inputsConsumed;
	}
}

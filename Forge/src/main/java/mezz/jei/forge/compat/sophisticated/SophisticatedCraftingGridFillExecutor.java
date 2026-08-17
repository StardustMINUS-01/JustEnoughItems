package mezz.jei.forge.compat.sophisticated;

import mezz.jei.common.bookmarks.CraftingStackMatcher;
import mezz.jei.common.bookmarks.ICraftingGridFillExecutor;
import mezz.jei.common.config.DebugConfig;
import mezz.jei.forge.compat.CompatUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Optional fill-grid executor for Sophisticated crafting upgrades.
 * Loaded only when SophisticatedCore is installed.
 */
public class SophisticatedCraftingGridFillExecutor implements ICraftingGridFillExecutor {
	private static final Logger LOGGER = LogManager.getLogger();

	public static Optional<SophisticatedCraftingGridFillExecutor> createIfLoaded() {
		return CompatUtil.createIfLoaded(
			"net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase",
			SophisticatedCraftingGridFillExecutor::new
		);
	}

	private SophisticatedCraftingGridFillExecutor() {
	}

	@Override
	public boolean canHandle(AbstractContainerMenu menu) {
		return SophisticatedCraftingGridAccess.find(menu).isPresent();
	}

	@Override
	public int fill(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu.containerId != containerId || targetStacks.isEmpty()) {
			return 0;
		}
		Optional<SophisticatedCraftingGridAccess> optionalAccess = SophisticatedCraftingGridAccess.find(menu);
		if (optionalAccess.isEmpty()) {
			return 0;
		}
		SophisticatedCraftingGridAccess access = optionalAccess.get();
		access.ensureOpen();

		// Same fill approach as Sophisticated's own JEI transfer: clear the old grid, pull from storage + player
		// inventory, and fill as many complete sets as available.
		ItemStack[] stacks = new ItemStack[SophisticatedCraftingGridAccess.GRID_SIZE];
		Arrays.fill(stacks, ItemStack.EMPTY);
		for (int i = 0; i < SophisticatedCraftingGridAccess.GRID_SIZE && i < targetStacks.size(); i++) {
			stacks[i] = targetStacks.get(i);
		}
		List<ItemStack> recipeStacks = Arrays.asList(stacks);
		ResourceLocation recipeId = SophisticatedCraftingGridAccess.resolveRecipeId(player, recipeStacks);
		if (recipeId == null) {
			return 0;
		}
		access.fillGrid(player, recipeId, recipeStacks);
		menu.slotsChanged(player.getInventory());

		int capped = capToMultiplier(player, access, recipeStacks, multiplier);
		if (capped > 0) {
			player.getInventory().setChanged();
			menu.broadcastChanges();
		}
		if (DebugConfig.isDebugModeEnabled()) {
			LOGGER.info("[Bug6] COMPAT-SOPHISTICATED-FILL containerId={} multiplier={} capped={}", containerId, multiplier, capped);
		}
		return capped;
	}

	private static int capToMultiplier(ServerPlayer player, SophisticatedCraftingGridAccess access, List<ItemStack> recipeStacks, int multiplier) {
		if (multiplier <= 0) {
			return countFilled(access);
		}
		List<Slot> craftingSlots = access.getCraftingSlots();
		for (int i = 0; i < craftingSlots.size(); i++) {
			Slot slot = craftingSlots.get(i);
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			int required = recipeStacks.get(i).getCount() * multiplier;
			if (stack.getCount() <= required) {
				continue;
			}
			ItemStack excess = stack.copy();
			excess.setCount(stack.getCount() - required);
			stack.shrink(excess.getCount());
			slot.setChanged();
			insertExcess(player, access, excess);
		}
		return countFilled(access);
	}

	private static void insertExcess(ServerPlayer player, SophisticatedCraftingGridAccess access, ItemStack excess) {
		int remaining = excess.getCount();
		for (int index : access.getInventorySlotIndexes()) {
			if (remaining <= 0) {
				break;
			}
			Slot slot = access.getContainer().getSlot(index);
			ItemStack existing = slot.getItem();
			if (existing.isEmpty() || !CraftingStackMatcher.matchesExactStack(existing, excess)) {
				continue;
			}
			int room = Math.min(slot.getMaxStackSize(existing), existing.getMaxStackSize()) - existing.getCount();
			if (room <= 0) {
				continue;
			}
			int inserted = Math.min(remaining, room);
			existing.grow(inserted);
			slot.setChanged();
			remaining -= inserted;
		}
		for (int index : access.getInventorySlotIndexes()) {
			if (remaining <= 0) {
				break;
			}
			Slot slot = access.getContainer().getSlot(index);
			if (slot.hasItem() || !slot.mayPlace(excess)) {
				continue;
			}
			ItemStack insertedStack = excess.copy();
			int inserted = Math.min(remaining, Math.min(slot.getMaxStackSize(insertedStack), insertedStack.getMaxStackSize()));
			insertedStack.setCount(inserted);
			slot.set(insertedStack);
			remaining -= inserted;
		}
		if (remaining > 0) {
			ItemStack leftover = excess.copy();
			leftover.setCount(remaining);
			player.getInventory().placeItemBackInInventory(leftover);
		}
	}

	private static int countFilled(SophisticatedCraftingGridAccess access) {
		int filled = 0;
		for (Slot slot : access.getCraftingSlots()) {
			if (!slot.getItem().isEmpty()) {
				filled++;
			}
		}
		return filled;
	}
}

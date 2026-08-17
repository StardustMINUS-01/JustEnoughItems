package mezz.jei.forge.compat.ae2;

import mezz.jei.common.bookmarks.ICraftingGridCraftExecutor;
import mezz.jei.common.bookmarks.ServerBookmarkCraftingGridFill;
import mezz.jei.common.config.DebugConfig;
import mezz.jei.forge.compat.CompatUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.CraftingTermMenu;

/**
 * Auto-crafting executor for AE2 crafting terminals and wireless crafting terminals (1.20.1).
 *
 * Unlike the 1.21.1 implementation (which injects a batch-crafting method into AE2's
 * CraftingTermSlot via mixin), the 1.20.1 port uses only the public CraftingTermMenu /
 * InternalInventory APIs: the grid is filled, then each batch consumes the grid contents
 * directly and inserts the result into the player inventory + ME network. Loaded only when
 * AE2 is installed.
 */
public class Ae2CraftingGridCraftExecutor implements ICraftingGridCraftExecutor {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int MAX_MULTIPLIER = 64;

	private final CraftingAccess craftingAccess;

	public static Optional<Ae2CraftingGridCraftExecutor> createIfLoaded() {
		return CompatUtil.createIfLoaded(
			"appeng.menu.me.items.CraftingTermMenu",
			() -> new Ae2CraftingGridCraftExecutor(new DirectCraftingAccess())
		);
	}

	private Ae2CraftingGridCraftExecutor(CraftingAccess craftingAccess) {
		this.craftingAccess = craftingAccess;
	}

	@Override
	public boolean canHandle(AbstractContainerMenu menu) {
		return craftingAccess.canHandle(menu);
	}

	@Override
	public int craft(ServerPlayer player, int containerId, @Nullable ResourceLocation recipeId, List<ItemStack> targetStacks, int multiplier) {
		return craftingAccess.craft(player, containerId, targetStacks, multiplier);
	}

	private interface CraftingAccess {
		boolean canHandle(AbstractContainerMenu menu);

		int craft(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier);
	}

	private static final class DirectCraftingAccess implements CraftingAccess {
		@Override
		public boolean canHandle(AbstractContainerMenu menu) {
			return menu instanceof CraftingTermMenu;
		}

		@Override
		public int craft(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier) {
			AbstractContainerMenu menu = player.containerMenu;
			if (!(menu instanceof CraftingTermMenu craftingMenu) || menu.containerId != containerId || targetStacks.isEmpty()) {
				return 0;
			}
			if (!clearGridToNetwork(craftingMenu)) {
				player.displayClientMessage(Component.translatable("jei.ae2.crafting.grid_full"), false);
				return 0;
			}
			Ae2NetworkMaterialSource networkSource = new Ae2NetworkMaterialSource(craftingMenu, player, targetStacks);

			List<Slot> craftingSlots = craftingMenu.getSlots(SlotSemantics.CRAFTING_GRID);
			List<Slot> resultSlots = craftingMenu.getSlots(SlotSemantics.CRAFTING_RESULT);
			if (craftingSlots.isEmpty() || resultSlots.isEmpty()) {
				return 0;
			}
			Slot resultSlot = resultSlots.get(0);

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
					craftingSlots,
					networkSource
				);
				if (filled <= 0) {
					break;
				}
				menu.slotsChanged(player.getInventory());
				int done = craftOneBatch(craftingMenu, player, resultSlot.getItem(), Math.min(filled, remaining));
				if (done <= 0) {
					break;
				}
				crafted += done;
				remaining -= done;
				menu.broadcastChanges();
			}
			if (crafted > 0) {
				player.getInventory().setChanged();
				menu.broadcastChanges();
			}
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] COMPAT-AE2-CRAFT containerId={} multiplier={} crafted={}", containerId, multiplier, crafted);
			}
			return crafted;
		}

		private static boolean clearGridToNetwork(CraftingTermMenu menu) {
			InternalInventory grid = menu.getCraftingMatrix();
			MEStorage storage = menu.getHost().getInventory();
			IActionSource actionSource = menu.getActionSource();
			boolean hasItems = false;
			for (int i = 0; i < grid.size(); i++) {
				if (!grid.getStackInSlot(i).isEmpty()) {
					hasItems = true;
					break;
				}
			}
			if (!hasItems) {
				return true;
			}
			for (int i = 0; i < grid.size(); i++) {
				ItemStack stack = grid.getStackInSlot(i);
				if (stack.isEmpty()) {
					continue;
				}
				AEItemKey key = AEItemKey.of(stack);
				long inserted = storage.insert(key, stack.getCount(), Actionable.MODULATE, actionSource);
				if (inserted < stack.getCount()) {
					ItemStack leftover = stack.copy();
					leftover.setCount((int) (stack.getCount() - inserted));
					grid.setItemDirect(i, leftover);
					return false;
				}
				grid.setItemDirect(i, ItemStack.EMPTY);
			}
			menu.slotsChanged(grid.toContainer());
			return true;
		}

		/**
		 * 1.20.1 port of the 1.21.1 CraftingTermSlotMixin#jei$craftBatch: consumes the grid
		 * directly, inserts the result into the player inventory and ME network (network portion
		 * first so a failure can restore the grid without duplicating items).
		 */
		private static int craftOneBatch(CraftingTermMenu menu, ServerPlayer player, ItemStack result, int times) {
			if (result.isEmpty() || times <= 0) {
				return 0;
			}
			InternalInventory grid = menu.getCraftingMatrix();
			int actualTimes = times;
			for (int i = 0; i < grid.size(); i++) {
				int count = grid.getStackInSlot(i).getCount();
				if (count > 0) {
					actualTimes = Math.min(actualTimes, count);
				}
			}
			if (actualTimes <= 0) {
				return 0;
			}

			// Simulate placement before consuming anything: only craft as many results as the player
			// inventory and the ME network can fully hold.
			int totalCount = result.getCount() * actualTimes;
			ItemStack totalResult = result.copy();
			totalResult.setCount(totalCount);
			int playerRoom = getInsertableCount(player.getInventory(), totalResult);
			long networkRoom = getNetworkRoom(menu, totalResult, totalCount - playerRoom);
			int maxTimes = (int) Math.min(actualTimes, (playerRoom + networkRoom) / result.getCount());
			if (maxTimes <= 0) {
				return 0;
			}
			actualTimes = maxTimes;
			totalCount = result.getCount() * actualTimes;
			totalResult = result.copy();
			totalResult.setCount(totalCount);
			playerRoom = getInsertableCount(player.getInventory(), totalResult);
			int remainderCount = totalCount - playerRoom;

			List<ItemStack> remainingItems = getRemainingItems(menu);
			List<ItemStack> consumed = new ArrayList<>(grid.size());
			List<ItemStack> replacements = new ArrayList<>(grid.size());
			for (int i = 0; i < grid.size(); i++) {
				if (grid.getStackInSlot(i).isEmpty()) {
					consumed.add(ItemStack.EMPTY);
					replacements.add(ItemStack.EMPTY);
					continue;
				}
				ItemStack remaining = i < remainingItems.size() ? remainingItems.get(i) : ItemStack.EMPTY;
				if (!remaining.isEmpty()) {
					// Tool / container ingredient (e.g. GTCEu hammers): the recipe returns it instead of
					// consuming it, so keep it in the grid (replaced by the returned stack, which carries
					// any per-craft damage). Consuming it would destroy the tool.
					consumed.add(ItemStack.EMPTY);
					replacements.add(remaining.copy());
					continue;
				}
				ItemStack extracted = grid.extractItem(i, actualTimes, false);
				if (extracted.getCount() < actualTimes) {
					restoreConsumed(grid, consumed);
					return 0;
				}
				consumed.add(extracted);
				replacements.add(ItemStack.EMPTY);
			}

			// Insert the network portion before touching the player inventory, so a network failure
			// can restore the grid without leaving duplicated items behind.
			if (remainderCount > 0) {
				ItemStack remainder = totalResult.copy();
				remainder.setCount(remainderCount);
				long inserted = insertIntoNetwork(menu, remainder, remainderCount);
				if (inserted < remainderCount) {
					restoreConsumed(grid, consumed);
					return 0;
				}
			}
			if (playerRoom > 0) {
				ItemStack playerPortion = totalResult.copy();
				playerPortion.setCount(playerRoom);
				addToPlayerInventory(player.getInventory(), playerPortion);
			}
			// Apply returned ingredients (tools/containers) only after everything succeeded.
			for (int i = 0; i < replacements.size(); i++) {
				ItemStack replacement = replacements.get(i);
				if (!replacement.isEmpty()) {
					grid.setItemDirect(i, replacement);
				}
			}
			menu.slotsChanged(grid.toContainer());
			return actualTimes;
		}

		/**
		 * Remaining items of the current crafting recipe for the grid slots. Ingredients that the
		 * recipe returns (tools, buckets, etc.) must not be consumed by the batch crafting.
		 */
		private static List<ItemStack> getRemainingItems(CraftingTermMenu menu) {
			Recipe<CraftingContainer> recipe = menu.getCurrentRecipe();
			if (recipe == null) {
				return Collections.emptyList();
			}
			InternalInventory grid = menu.getCraftingMatrix();
			TransientCraftingContainer container = new TransientCraftingContainer(menu, 3, 3);
			for (int i = 0; i < grid.size() && i < 9; i++) {
				container.setItem(i, grid.getStackInSlot(i).copy());
			}
			return recipe.getRemainingItems(container);
		}

		private static long getNetworkRoom(CraftingTermMenu menu, ItemStack totalResult, int requested) {
			if (requested <= 0) {
				return 0;
			}
			MEStorage storage = menu.getHost().getInventory();
			return storage.insert(AEItemKey.of(totalResult), requested, Actionable.SIMULATE, menu.getActionSource());
		}

		private static long insertIntoNetwork(CraftingTermMenu menu, ItemStack remainder, int amount) {
			MEStorage storage = menu.getHost().getInventory();
			return storage.insert(AEItemKey.of(remainder), amount, Actionable.MODULATE, menu.getActionSource());
		}

		private static void restoreConsumed(InternalInventory grid, List<ItemStack> consumed) {
			for (int i = 0; i < consumed.size(); i++) {
				ItemStack stack = consumed.get(i);
				if (!stack.isEmpty()) {
					grid.setItemDirect(i, stack);
				}
			}
		}

		private static int getInsertableCount(Inventory inventory, ItemStack stack) {
			int remaining = stack.getCount();
			for (ItemStack slotStack : inventory.items) {
				if (remaining <= 0) {
					break;
				}
				if (slotStack.isEmpty()) {
					remaining = Math.max(0, remaining - stack.getMaxStackSize());
				} else if (mezz.jei.common.bookmarks.CraftingStackMatcher.matchesExactStack(slotStack, stack) && slotStack.getCount() < slotStack.getMaxStackSize()) {
					remaining = Math.max(0, remaining - (slotStack.getMaxStackSize() - slotStack.getCount()));
				}
			}
			return Math.max(0, stack.getCount() - remaining);
		}

		private static void addToPlayerInventory(Inventory inventory, ItemStack stack) {
			int remaining = stack.getCount();
			for (ItemStack slotStack : inventory.items) {
				if (remaining <= 0) {
					return;
				}
				if (!slotStack.isEmpty() && mezz.jei.common.bookmarks.CraftingStackMatcher.matchesExactStack(slotStack, stack) && slotStack.getCount() < slotStack.getMaxStackSize()) {
					int inserted = Math.min(remaining, slotStack.getMaxStackSize() - slotStack.getCount());
					slotStack.grow(inserted);
					remaining -= inserted;
				}
			}
			for (int i = 0; i < inventory.items.size() && remaining > 0; i++) {
				ItemStack slotStack = inventory.items.get(i);
				if (!slotStack.isEmpty()) {
					continue;
				}
				ItemStack insertedStack = stack.copy();
				int inserted = Math.min(remaining, insertedStack.getMaxStackSize());
				insertedStack.setCount(inserted);
				inventory.items.set(i, insertedStack);
				remaining -= inserted;
			}
		}
	}
}

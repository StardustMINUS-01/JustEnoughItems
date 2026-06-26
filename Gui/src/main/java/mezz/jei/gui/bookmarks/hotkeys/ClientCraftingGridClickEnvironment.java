/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks.hotkeys;

import mezz.jei.common.bookmarks.CraftingStackMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Forge 1.20.1 click adapter for GTNH NEI FastTransferManager clickSlot semantics.
 */
final class ClientCraftingGridClickEnvironment implements ClientCraftingGridClickTask.Environment {
	private static final int RESULT_SLOT = 0;
	private static final int VANILLA_CRAFTING_FIRST_SLOT = 1;
	private static final int PLAYER_INVENTORY_CRAFTING_SLOT_COUNT = 4;
	private static final int CRAFTING_TABLE_SLOT_COUNT = 9;

	private final Minecraft minecraft;
	private final AbstractContainerMenu menu;
	private final int containerId;
	private final int craftingSlotCount;

	private ClientCraftingGridClickEnvironment(Minecraft minecraft, AbstractContainerMenu menu, int craftingSlotCount) {
		this.minecraft = minecraft;
		this.menu = menu;
		this.containerId = menu.containerId;
		this.craftingSlotCount = craftingSlotCount;
	}

	static Optional<ClientCraftingGridClickEnvironment> create(AbstractContainerMenu menu) {
		int craftingSlotCount = getCraftingSlotCount(menu);
		if (craftingSlotCount <= 0 || menu.slots.size() <= RESULT_SLOT || menu.slots.size() < VANILLA_CRAFTING_FIRST_SLOT + craftingSlotCount) {
			return Optional.empty();
		}
		return Optional.of(new ClientCraftingGridClickEnvironment(Minecraft.getInstance(), menu, craftingSlotCount));
	}

	@Override
	public boolean stillValid() {
		LocalPlayer player = minecraft.player;
		return player != null &&
			minecraft.gameMode != null &&
			player.containerMenu == menu &&
			menu.containerId == containerId &&
			getCraftingSlotCount(menu) == craftingSlotCount;
	}

	@Override
	public int craftingSlotCount() {
		return craftingSlotCount;
	}

	@Override
	public ItemStack getCraftingStack(int slotIndex) {
		return menu.getSlot(toCraftingMenuSlot(slotIndex)).getItem().copy();
	}

	@Override
	public boolean carriedStackEmpty() {
		return menu.getCarried().isEmpty();
	}

	@Override
	public boolean clearCraftingSlot(int slotIndex) {
		int menuSlot = toCraftingMenuSlot(slotIndex);
		Slot slot = menu.getSlot(menuSlot);
		if (!slot.hasItem()) {
			return true;
		}
		if (!canClickSlot(slot)) {
			return false;
		}
		click(menuSlot, 0, ClickType.QUICK_MOVE);
		return true;
	}

	@Override
	public boolean fillCraftingSlot(int slotIndex, ItemStack targetStack) {
		if (targetStack.isEmpty() || !carriedStackEmpty()) {
			return false;
		}
		int targetMenuSlot = toCraftingMenuSlot(slotIndex);
		Slot targetSlot = menu.getSlot(targetMenuSlot);
		if (!targetSlot.mayPlace(targetStack)) {
			return false;
		}
		for (int i = 0; i < targetStack.getCount(); i++) {
			int sourceMenuSlot = findSourceSlot(targetStack);
			if (sourceMenuSlot < 0) {
				return false;
			}
			click(sourceMenuSlot, 0, ClickType.PICKUP);
			click(targetMenuSlot, 1, ClickType.PICKUP);
			click(sourceMenuSlot, 0, ClickType.PICKUP);
			if (!carriedStackEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean hasResult() {
		return !menu.getSlot(RESULT_SLOT).getItem().isEmpty();
	}

	@Override
	public boolean takeResult() {
		Slot resultSlot = menu.getSlot(RESULT_SLOT);
		if (!resultSlot.hasItem() || !canClickSlot(resultSlot)) {
			return false;
		}
		click(RESULT_SLOT, 0, ClickType.QUICK_MOVE);
		return true;
	}

	private int findSourceSlot(ItemStack targetStack) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return -1;
		}
		List<Slot> slots = menu.slots;
		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			if (slot.container != player.getInventory() ||
				slot.getContainerSlot() < 0 ||
				slot.getContainerSlot() >= Inventory.INVENTORY_SIZE ||
				!slot.mayPickup(player)) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty() && CraftingStackMatcher.matchesIngredientTemplate(targetStack, stack)) {
				return i;
			}
		}
		return -1;
	}

	private boolean canClickSlot(Slot slot) {
		LocalPlayer player = minecraft.player;
		return player != null && slot.mayPickup(player);
	}

	private void click(int slotIndex, int button, ClickType clickType) {
		LocalPlayer player = minecraft.player;
		@Nullable MultiPlayerGameMode gameMode = minecraft.gameMode;
		if (player != null && gameMode != null) {
			gameMode.handleInventoryMouseClick(containerId, slotIndex, button, clickType, player);
		}
	}

	private static int getCraftingSlotCount(AbstractContainerMenu menu) {
		if (menu instanceof InventoryMenu) {
			return PLAYER_INVENTORY_CRAFTING_SLOT_COUNT;
		}
		if (menu instanceof CraftingMenu) {
			return CRAFTING_TABLE_SLOT_COUNT;
		}
		return 0;
	}

	private static int toCraftingMenuSlot(int slotIndex) {
		return VANILLA_CRAFTING_FIRST_SLOT + slotIndex;
	}
}

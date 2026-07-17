/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.common.bookmarks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Server-side equivalent of the vanilla crafting-grid part of GTNH NEI's DefaultOverlayHandler.
 */
public final class ServerBookmarkCraftingGridFill {
	private static final int MAX_MULTIPLIER = 64;

	private ServerBookmarkCraftingGridFill() {
	}

	public static int fill(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier) {
		return fill(player.containerMenu, containerId, player.getInventory(), player, targetStacks, multiplier);
	}

	public static int fill(
		AbstractContainerMenu menu,
		int containerId,
		Container playerInventory,
		@Nullable ServerPlayer player,
		List<ItemStack> targetStacks,
		int multiplier
	) {
		if (menu.containerId != containerId || targetStacks.isEmpty()) {
			return 0;
		}

		List<Slot> craftingSlots = VanillaCraftingGridSlots.getCraftingSlots(menu);
		if (craftingSlots.isEmpty()) {
			return 0;
		}

		List<ItemStack> recipeStacks = normalizeTargetStacks(targetStacks, craftingSlots.size());
		if (recipeStacks.stream().allMatch(ItemStack::isEmpty)) {
			return 0;
		}

		List<Slot> playerSlots = getPlayerSlots(menu, playerInventory);
		if (!canStowCraftingGrid(craftingSlots, playerSlots)) {
			return 0;
		}

		int craftCount = calculateCraftCount(playerSlots, craftingSlots, recipeStacks, multiplier);
		if (craftCount <= 0) {
			return 0;
		}

		stowCraftingGrid(craftingSlots, playerSlots);
		int filled = fillCraftingSlots(playerSlots, craftingSlots, recipeStacks, craftCount, player);
		if (filled <= 0) {
			return 0;
		}

		playerInventory.setChanged();
		menu.broadcastChanges();
		return filled;
	}

	static List<Slot> getPlayerSlots(AbstractContainerMenu menu, Container playerInventory) {
		return menu.slots.stream()
			.filter(slot -> slot.container == playerInventory)
			.filter(slot -> slot.getContainerSlot() >= 0 && slot.getContainerSlot() < Inventory.INVENTORY_SIZE)
			.toList();
	}

	private static List<ItemStack> normalizeTargetStacks(List<ItemStack> targetStacks, int slotCount) {
		List<ItemStack> result = new ArrayList<>(slotCount);
		for (int i = 0; i < slotCount; i++) {
			ItemStack stack = i < targetStacks.size() ? targetStacks.get(i) : ItemStack.EMPTY;
			result.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}
		return result;
	}

	private static boolean canStowCraftingGrid(List<Slot> craftingSlots, List<Slot> playerSlots) {
		List<ItemStack> simulated = playerSlots.stream()
			.map(slot -> slot.getItem().copy())
			.collect(Collectors.toCollection(ArrayList::new));
		for (Slot slot : craftingSlots) {
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty() && !simulateInsert(simulated, playerSlots, stack.copy())) {
				return false;
			}
		}
		return true;
	}

	static boolean canInsert(List<Slot> playerSlots, ItemStack stack) {
		List<ItemStack> simulated = playerSlots.stream()
			.map(slot -> slot.getItem().copy())
			.collect(Collectors.toCollection(ArrayList::new));
		return simulateInsert(simulated, playerSlots, stack.copy());
	}

	private static boolean simulateInsert(List<ItemStack> simulated, List<Slot> playerSlots, ItemStack stack) {
		int remaining = stack.getCount();
		for (int i = 0; i < simulated.size() && remaining > 0; i++) {
			ItemStack existing = simulated.get(i);
			if (existing.isEmpty() || !CraftingStackMatcher.matchesExactStack(existing, stack)) {
				continue;
			}
			int inserted = Math.min(remaining, getRoom(playerSlots.get(i), existing));
			existing.grow(inserted);
			remaining -= inserted;
		}
		for (int i = 0; i < simulated.size() && remaining > 0; i++) {
			ItemStack existing = simulated.get(i);
			if (!existing.isEmpty() || !playerSlots.get(i).mayPlace(stack)) {
				continue;
			}
			ItemStack insertedStack = stack.copy();
			int inserted = Math.min(remaining, Math.min(playerSlots.get(i).getMaxStackSize(insertedStack), insertedStack.getMaxStackSize()));
			insertedStack.setCount(inserted);
			simulated.set(i, insertedStack);
			remaining -= inserted;
		}
		return remaining <= 0;
	}

	private static int calculateCraftCount(List<Slot> playerSlots, List<Slot> craftingSlots, List<ItemStack> recipeStacks, int multiplier) {
		int craftCount = multiplier == 0 ? MAX_MULTIPLIER : Math.min(MAX_MULTIPLIER, Math.max(1, multiplier));
		for (ItemStack recipeStack : recipeStacks) {
			if (recipeStack.isEmpty()) {
				continue;
			}
			int available = countAvailable(playerSlots, craftingSlots, recipeStack);
			int requiredPerCraft = countRequiredPerCraft(recipeStacks, recipeStack);
			int stackLimit = Math.min(recipeStack.getMaxStackSize(), minTargetSlotLimit(craftingSlots, recipeStacks, recipeStack));
			int slotCapacity = stackLimit / recipeStack.getCount();
			craftCount = Math.min(craftCount, available / requiredPerCraft);
			craftCount = Math.min(craftCount, slotCapacity);
		}
		return craftCount;
	}

	private static int countAvailable(List<Slot> playerSlots, List<Slot> craftingSlots, ItemStack target) {
		int count = 0;
		for (Slot slot : playerSlots) {
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty() && CraftingStackMatcher.matchesIngredientTemplate(target, stack)) {
				count += stack.getCount();
			}
		}
		for (Slot slot : craftingSlots) {
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty() && CraftingStackMatcher.matchesIngredientTemplate(target, stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static int countRequiredPerCraft(List<ItemStack> recipeStacks, ItemStack target) {
		int count = 0;
		for (ItemStack stack : recipeStacks) {
			if (!stack.isEmpty() && CraftingStackMatcher.matchesIngredientTemplate(target, stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static int minTargetSlotLimit(List<Slot> craftingSlots, List<ItemStack> recipeStacks, ItemStack target) {
		int limit = Integer.MAX_VALUE;
		for (int i = 0; i < recipeStacks.size(); i++) {
			ItemStack stack = recipeStacks.get(i);
			if (!stack.isEmpty() && CraftingStackMatcher.matchesIngredientTemplate(target, stack)) {
				limit = Math.min(limit, craftingSlots.get(i).getMaxStackSize(stack));
			}
		}
		return limit == Integer.MAX_VALUE ? target.getMaxStackSize() : limit;
	}

	private static void stowCraftingGrid(List<Slot> craftingSlots, List<Slot> playerSlots) {
		for (Slot craftingSlot : craftingSlots) {
			ItemStack stack = craftingSlot.getItem();
			if (!stack.isEmpty()) {
				insert(playerSlots, stack.copy());
				craftingSlot.set(ItemStack.EMPTY);
				craftingSlot.setChanged();
			}
		}
	}

	private static int fillCraftingSlots(List<Slot> playerSlots, List<Slot> craftingSlots, List<ItemStack> recipeStacks, int craftCount, @Nullable ServerPlayer player) {
		int filled = 0;
		for (int i = 0; i < recipeStacks.size(); i++) {
			ItemStack recipeStack = recipeStacks.get(i);
			if (recipeStack.isEmpty()) {
				continue;
			}
			Slot targetSlot = craftingSlots.get(i);
			if (!targetSlot.mayPlace(recipeStack)) {
				continue;
			}
			int amount = recipeStack.getCount() * craftCount;
			ItemStack moved = extract(playerSlots, recipeStack, amount, player);
			if (moved.isEmpty()) {
				continue;
			}
			targetSlot.set(moved);
			targetSlot.setChanged();
			filled = Math.max(filled, moved.getCount() / recipeStack.getCount());
		}
		return filled;
	}

	private static ItemStack extract(List<Slot> playerSlots, ItemStack target, int amount, @Nullable ServerPlayer player) {
		ItemStack result = ItemStack.EMPTY;
		int remaining = amount;
		for (Slot slot : playerSlots) {
			if (remaining <= 0) {
				break;
			}
			if (player != null && !slot.mayPickup(player)) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || !CraftingStackMatcher.matchesIngredientTemplate(target, stack)) {
				continue;
			}
			int extracted = Math.min(remaining, stack.getCount());
			if (result.isEmpty()) {
				result = stack.copy();
				result.setCount(0);
			}
			stack.shrink(extracted);
			slot.setChanged();
			result.grow(extracted);
			remaining -= extracted;
		}
		return result;
	}

	static void insert(List<Slot> playerSlots, ItemStack stack) {
		int remaining = stack.getCount();
		for (Slot slot : playerSlots) {
			if (remaining <= 0) {
				return;
			}
			ItemStack existing = slot.getItem();
			if (existing.isEmpty() || !CraftingStackMatcher.matchesExactStack(existing, stack)) {
				continue;
			}
			int inserted = Math.min(remaining, getRoom(slot, existing));
			if (inserted > 0) {
				existing.grow(inserted);
				slot.setChanged();
				remaining -= inserted;
			}
		}
		for (Slot slot : playerSlots) {
			if (remaining <= 0) {
				return;
			}
			if (slot.hasItem() || !slot.mayPlace(stack)) {
				continue;
			}
			ItemStack insertedStack = stack.copy();
			int inserted = Math.min(remaining, Math.min(slot.getMaxStackSize(insertedStack), insertedStack.getMaxStackSize()));
			insertedStack.setCount(inserted);
			slot.set(insertedStack);
			remaining -= inserted;
		}
	}

	private static int getRoom(Slot slot, ItemStack existing) {
		return Math.min(slot.getMaxStackSize(existing), existing.getMaxStackSize()) - existing.getCount();
	}
}

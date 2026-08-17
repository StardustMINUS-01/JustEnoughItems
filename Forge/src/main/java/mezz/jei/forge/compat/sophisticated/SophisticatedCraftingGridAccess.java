package mezz.jei.forge.compat.sophisticated;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import net.p3pp3rf1y.sophisticatedcore.compat.recipeviewers.common.CraftingContainerRecipeTransferHandlerServer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.crafting.CraftingUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.util.RecipeHelper;

/**
 * Server-side access to the Sophisticated crafting upgrade container.
 * Ported from JEI 1.21.1 and adapted to the 1.20.1 Forge API.
 */
final class SophisticatedCraftingGridAccess {
	static final int GRID_SIZE = 9;

	private final StorageContainerMenuBase<?> container;
	private final CraftingUpgradeContainer craftingContainer;
	private final List<Slot> craftingSlots;
	private final List<Integer> craftingSlotIndexes;
	private final List<Integer> inventorySlotIndexes;
	private final Slot resultSlot;

	private SophisticatedCraftingGridAccess(
		StorageContainerMenuBase<?> container,
		CraftingUpgradeContainer craftingContainer,
		List<Slot> craftingSlots,
		Slot resultSlot
	) {
		this.container = container;
		this.craftingContainer = craftingContainer;
		this.craftingSlots = craftingSlots;
		this.craftingSlotIndexes = craftingSlots.stream().map(s -> s.index).sorted().toList();
		this.inventorySlotIndexes = container.slots.stream()
			.filter(s -> !craftingSlotIndexes.contains(s.index))
			.map(s -> s.index)
			.sorted()
			.toList();
		this.resultSlot = resultSlot;
	}

	static Optional<SophisticatedCraftingGridAccess> find(AbstractContainerMenu menu) {
		if (!(menu instanceof StorageContainerMenuBase<?> container)) {
			return Optional.empty();
		}
		Optional<CraftingUpgradeContainer> optionalCrafting = container.getOpenOrFirstCraftingContainer(RecipeType.CRAFTING);
		if (optionalCrafting.isEmpty()) {
			return Optional.empty();
		}
		CraftingUpgradeContainer craftingContainer = optionalCrafting.get();
		List<Slot> craftingSlots = craftingContainer.getRecipeSlots();
		if (craftingSlots.size() != GRID_SIZE) {
			// Contract defense: ICraftingContainer implementations provide 9 crafting slots; abnormal shapes are rejected.
			return Optional.empty();
		}
		return Optional.of(new SophisticatedCraftingGridAccess(container, craftingContainer, craftingSlots, craftingContainer.getSlots().get(GRID_SIZE)));
	}

	StorageContainerMenuBase<?> getContainer() {
		return container;
	}

	List<Slot> getCraftingSlots() {
		return craftingSlots;
	}

	List<Integer> getInventorySlotIndexes() {
		return inventorySlotIndexes;
	}

	Slot getResultSlot() {
		return resultSlot;
	}

	void ensureOpen() {
		if (craftingContainer.isOpen()) {
			return;
		}
		container.getOpenContainer().ifPresent(c -> {
			c.setIsOpen(false);
			container.setOpenTabId(-1);
		});
		craftingContainer.setIsOpen(true);
		container.setOpenTabId(craftingContainer.getUpgradeContainerId());
	}

	void fillGrid(ServerPlayer player, ResourceLocation recipeId, List<ItemStack> recipeStacks) {
		CraftingContainerRecipeTransferHandlerServer.setItemsWithStacks(
			player,
			recipeId,
			RecipeType.CRAFTING,
			recipeStacks,
			craftingSlotIndexes,
			inventorySlotIndexes,
			true
		);
	}

	@Nullable
	static ResourceLocation resolveRecipeId(ServerPlayer player, List<ItemStack> targetStacks) {
		ItemStack[] stacks = new ItemStack[GRID_SIZE];
		java.util.Arrays.fill(stacks, ItemStack.EMPTY);
		for (int i = 0; i < GRID_SIZE && i < targetStacks.size(); i++) {
			ItemStack stack = targetStacks.get(i);
			stacks[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
		}
		TransientCraftingContainer input = new TransientCraftingContainer(player.containerMenu, 3, 3);
		for (int i = 0; i < GRID_SIZE; i++) {
			input.setItem(i, stacks[i]);
		}
		return RecipeHelper.safeGetRecipesFor(RecipeType.CRAFTING, input, player.level()).stream()
			.findFirst()
			.map(recipe -> recipe.getId())
			.orElse(null);
	}
}

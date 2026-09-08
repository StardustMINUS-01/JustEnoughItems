package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipInventoryProvider;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlay;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAvailableStacksProviders;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayTargetSlots;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlayerInventoryRecipeChainTooltipInventoryProvider implements RecipeChainTooltipInventoryProvider {
	private final Minecraft minecraft;
	private final IIngredientManager ingredientManager;

	public PlayerInventoryRecipeChainTooltipInventoryProvider(Minecraft minecraft, IIngredientManager ingredientManager) {
		this.minecraft = minecraft;
		this.ingredientManager = ingredientManager;
	}

	@Override
	public List<RecipeChainInput> getInventoryInputs(int groupId, int firstSyntheticIndex) {
		return toInventoryInputs(groupId, firstSyntheticIndex, getAvailableStacks());
	}

	public List<RecipeChainInput> getTreeInventoryInputs(int groupId, @Nullable AbstractContainerMenu sourceMenu) {
		if (minecraft.player == null) {
			return List.of();
		}
		return toInventoryInputs(groupId, -1, getTreeAvailableStacks(minecraft.player.getInventory(), minecraft.player.containerMenu, sourceMenu));
	}

	private List<RecipeChainInput> toInventoryInputs(int groupId, int firstSyntheticIndex, List<ItemStack> stacks) {
		List<RecipeChainInput> inputs = new ArrayList<>();
		int index = firstSyntheticIndex;
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				continue;
			}
			int currentIndex = index;
			createTypedIngredient(stack).ifPresent(ingredient -> {
				BookmarkIngredientKey key = createKey(ingredient);
				inputs.add(new RecipeChainInput(
					currentIndex,
					BookmarkItemMetadataFactory.createForCraftingAvailable(groupId, ingredient, stack.getCount(), ingredientManager),
					key
				));
			});
			index--;
		}
		return List.copyOf(inputs);
	}

	public List<RecipeChainInput> getTooltipInventoryInputs(int groupId) {
		return minecraft.screen instanceof RecipesGui ? List.of() : getInventoryInputs(groupId, -1);
	}

	public List<ItemStack> getAvailableStacks() {
		if (minecraft.player == null) {
			return List.of();
		}
		return getAvailableStacks(minecraft.player.getInventory(), minecraft.player.containerMenu);
	}

	public static List<ItemStack> getAvailableStacks(Inventory inventory, AbstractContainerMenu menu) {
		return getAvailableStacks(inventory, menu, BookmarkAvailableStacksProviders.getAvailableStacks(menu));
	}

	public static List<ItemStack> getTreeAvailableStacks(Inventory inventory, AbstractContainerMenu currentMenu, @Nullable AbstractContainerMenu sourceMenu) {
		// Do not query a closed source or silently switch the tree to an unrelated container.
		if (sourceMenu == null || sourceMenu != currentMenu) {
			return inventory.items.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
		}
		List<ItemStack> storage = BookmarkExternalStorageSnapshots.readEntries(sourceMenu)
			.map(BookmarkExternalStorageSnapshots::toAvailableStacks)
			.orElseGet(() -> BookmarkAvailableStacksProviders.getAvailableStacks(sourceMenu));
		return getAvailableStacks(inventory, sourceMenu, storage);
	}

	private static List<ItemStack> getAvailableStacks(Inventory inventory, AbstractContainerMenu menu, List<ItemStack> storage) {
		List<ItemStack> stacks = new ArrayList<>();
		for (ItemStack stack : inventory.items) {
			if (!stack.isEmpty()) {
				stacks.add(stack.copy());
			}
		}
		for (BookmarkGhostOverlay.TargetSlot targetSlot : BookmarkGhostOverlayTargetSlots.fromMenu(menu)) {
			ItemStack stack = targetSlot.currentStack();
			if (!stack.isEmpty()) {
				stacks.add(stack.copy());
			}
		}
		stacks.addAll(storage);
		return List.copyOf(stacks);
	}

	private Optional<ITypedIngredient<ItemStack>> createTypedIngredient(ItemStack stack) {
		ItemStack normalized = stack.copy();
		normalized.setCount(1);
		return ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, normalized);
	}

	private BookmarkIngredientKey createKey(ITypedIngredient<ItemStack> ingredient) {
		return BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager);
	}
}

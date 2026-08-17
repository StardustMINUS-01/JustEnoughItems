package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAvailableStacksProviders;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipInventoryProvider;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlay;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayTargetSlots;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

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
	public List<RecipeChainInput> getInventoryInputs(String groupId, int firstSyntheticIndex) {
		if (minecraft.player == null) {
			return List.of();
		}
		List<RecipeChainInput> inputs = new ArrayList<>();
		int index = firstSyntheticIndex;
		for (ItemStack stack : getAvailableStacks()) {
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

	public List<ItemStack> getAvailableStacks() {
		if (minecraft.player == null) {
			return List.of();
		}
		List<ItemStack> stacks = new ArrayList<>(getAvailableStacks(minecraft.player.getInventory(), minecraft.player.containerMenu));
		stacks.addAll(BookmarkAvailableStacksProviders.getAvailableStacks(minecraft.player.containerMenu));
		return List.copyOf(stacks);
	}

	public static List<ItemStack> getAvailableStacks(Inventory inventory, AbstractContainerMenu menu) {
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

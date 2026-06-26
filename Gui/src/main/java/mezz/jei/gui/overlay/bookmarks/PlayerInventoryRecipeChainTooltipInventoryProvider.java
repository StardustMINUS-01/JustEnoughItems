package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipInventoryProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
		Inventory inventory = minecraft.player.getInventory();
		Map<BookmarkIngredientKey, Long> amounts = new LinkedHashMap<>();
		for (ItemStack stack : inventory.items) {
			if (stack.isEmpty()) {
				continue;
			}
			createKey(stack).ifPresent(key -> amounts.merge(key, (long) stack.getCount(), PlayerInventoryRecipeChainTooltipInventoryProvider::saturatedAdd));
		}
		List<RecipeChainInput> inputs = new ArrayList<>();
		int index = firstSyntheticIndex;
		for (Map.Entry<BookmarkIngredientKey, Long> entry : amounts.entrySet()) {
			inputs.add(new RecipeChainInput(index--, createMetadata(groupId, entry.getKey(), entry.getValue()), entry.getKey()));
		}
		return List.copyOf(inputs);
	}

	private Optional<BookmarkIngredientKey> createKey(ItemStack stack) {
		ItemStack normalized = stack.copy();
		normalized.setCount(1);
		return ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, normalized)
			.map(this::createKey);
	}

	private BookmarkIngredientKey createKey(ITypedIngredient<ItemStack> ingredient) {
		return BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager);
	}

	private static BookmarkItemMetadata createMetadata(String groupId, BookmarkIngredientKey key, long amount) {
		return new BookmarkItemMetadata(
			groupId == null ? BookmarkGroupManager.DEFAULT_GROUP_ID : groupId,
			BookmarkItemType.ITEM,
			1,
			amount,
			BookmarkItemMetadata.CHANCE_FULL,
			null,
			null,
			Set.of(key)
		);
	}

	private static long saturatedAdd(long first, long second) {
		try {
			return Math.addExact(first, second);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}
}

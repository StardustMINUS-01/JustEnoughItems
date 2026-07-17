package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BookmarkPullPlanner {
	private BookmarkPullPlanner() {
	}

	public static BookmarkPullPlan plan(
		List<RecipeChainInput> inputs,
		Set<ResourceLocation> collapsedRecipes,
		Map<BookmarkIngredientKey, Long> playerInventory,
		Map<BookmarkIngredientKey, Long> containerStorage,
		int freeSlots,
		int maxStackSize,
		boolean shift
	) {
		if (freeSlots <= 0 || maxStackSize <= 0) {
			return new BookmarkPullPlan(Map.of());
		}
		long maxPullAmount = saturatedMultiply(freeSlots, maxStackSize);
		RecipeChainDetails details = RecipeChainMath.refresh(inputs, collapsedRecipes);
		Map<BookmarkIngredientKey, Long> amounts = details.outputRecipes().isEmpty() ?
			planInitialOnlyPull(inputs, playerInventory, shift, maxPullAmount) :
			planRecipePull(inputs, collapsedRecipes, playerInventory, containerStorage, shift, maxPullAmount);
		return new BookmarkPullPlan(amounts);
	}

	private static Map<BookmarkIngredientKey, Long> planInitialOnlyPull(
		List<RecipeChainInput> inputs,
		Map<BookmarkIngredientKey, Long> playerInventory,
		boolean shift,
		long maxPullAmount
	) {
		Map<BookmarkIngredientKey, Long> amounts = new LinkedHashMap<>();
		for (RecipeChainInput input : inputs) {
			BookmarkItemMetadata metadata = input.metadata();
			if (metadata.type().isCatalyst()) {
				continue;
			}
			if (metadata.recipeUid() != null && metadata.type().isRecipeAssociated()) {
				continue;
			}
			primaryKey(metadata, key -> {
				long playerAmount = shift ? playerInventory.getOrDefault(key, 0L) : 0L;
				long amount = Math.max(0, metadata.amount() - playerAmount);
				addCapped(amounts, key, amount, maxPullAmount);
			});
		}
		return amounts;
	}

	private static Map<BookmarkIngredientKey, Long> planRecipePull(
		List<RecipeChainInput> inputs,
		Set<ResourceLocation> collapsedRecipes,
		Map<BookmarkIngredientKey, Long> playerInventory,
		Map<BookmarkIngredientKey, Long> containerStorage,
		boolean shift,
		long maxPullAmount
	) {
		List<RecipeChainInput> adjustedInputs = new ArrayList<>();
		int nextIndex = inputs.stream()
			.mapToInt(RecipeChainInput::index)
			.max()
			.orElse(-1) + 1;
		for (RecipeChainInput input : inputs) {
			BookmarkItemMetadata metadata = input.metadata();
			if (metadata.type().isCatalyst()) {
				continue;
			}
			if (metadata.recipeUid() == null || !metadata.type().isRecipeAssociated()) {
				final int sourceIndex = input.index();
				primaryKey(metadata, key -> {
					long playerAmount = shift ? playerInventory.getOrDefault(key, 0L) : 0L;
					long containerAmount = containerStorage.getOrDefault(key, 0L);
					long amount = Math.max(0, metadata.amount() - playerAmount - containerAmount);
					if (amount > 0) {
						adjustedInputs.add(new RecipeChainInput(sourceIndex, itemMetadata(key, amount)));
					}
				});
			} else {
				adjustedInputs.add(input);
			}
		}
		if (shift) {
			for (Map.Entry<BookmarkIngredientKey, Long> entry : playerInventory.entrySet()) {
				if (entry.getValue() > 0) {
					adjustedInputs.add(new RecipeChainInput(nextIndex++, itemMetadata(entry.getKey(), entry.getValue())));
				}
			}
		}
		Map<Integer, BookmarkIngredientKey> containerKeysByIndex = new LinkedHashMap<>();
		for (Map.Entry<BookmarkIngredientKey, Long> entry : containerStorage.entrySet()) {
			if (entry.getValue() > 0) {
				int index = nextIndex++;
				containerKeysByIndex.put(index, entry.getKey());
				adjustedInputs.add(new RecipeChainInput(index, itemMetadata(entry.getKey(), entry.getValue())));
			}
		}

		RecipeChainDetails details = RecipeChainMath.refresh(adjustedInputs, collapsedRecipes);
		Map<BookmarkIngredientKey, Long> amounts = new LinkedHashMap<>();
		for (Map.Entry<Integer, BookmarkIngredientKey> entry : containerKeysByIndex.entrySet()) {
			RecipeChainItem item = details.calculatedItems().get(entry.getKey());
			if (item != null && item.requiredAmount() > 0) {
				long available = containerStorage.getOrDefault(entry.getValue(), 0L);
				addCapped(amounts, entry.getValue(), Math.min(available, item.requiredAmount()), maxPullAmount);
			}
		}
		return amounts;
	}

	private static void primaryKey(BookmarkItemMetadata metadata, KeyConsumer consumer) {
		metadata.permutations().stream()
			.findFirst()
			.ifPresent(consumer::accept);
	}

	private static BookmarkItemMetadata itemMetadata(BookmarkIngredientKey key, long amount) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.ITEM,
			1,
			amount,
			BookmarkItemMetadata.CHANCE_FULL,
			null,
			null,
			Set.of(key)
		);
	}

	private static void addCapped(Map<BookmarkIngredientKey, Long> amounts, BookmarkIngredientKey key, long amount, long maxPullAmount) {
		if (amount <= 0) {
			return;
		}
		amounts.merge(key, Math.min(amount, maxPullAmount), (first, second) -> Math.min(saturatedAdd(first, second), maxPullAmount));
	}

	private static long saturatedAdd(long first, long second) {
		try {
			return Math.addExact(first, second);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	private static long saturatedMultiply(long first, long second) {
		try {
			return Math.multiplyExact(first, second);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	private interface KeyConsumer {
		void accept(BookmarkIngredientKey key);
	}

	public record BookmarkPullPlan(Map<BookmarkIngredientKey, Long> amounts) {
		public BookmarkPullPlan {
			amounts = Map.copyOf(amounts);
		}
	}
}

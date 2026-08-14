package mezz.jei.gui.favorites;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientAmountResolver;
import mezz.jei.gui.bookmarks.RecipeTreeBookmarkEntry;
import mezz.jei.gui.bookmarks.RecipeTreeBookmarkGroup;
import mezz.jei.gui.bookmarks.RecipeTreeSlotIngredient;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the favorite recipe tree into bookmark groups, ported from
 * JEI 1.21.1 ({@code BookmarkList#createRecipeBookmarkEntries} combined with
 * {@code FavoriteRecipeGridSource#mergeRecipeInputs} and
 * {@code BookmarkList#getRecipeBookmarkGroupTitle}).
 *
 * <p>Each recipe of the tree becomes one {@link RecipeTreeBookmarkGroup} whose
 * title is the display name of the recipe's primary output (falling back to
 * the first input, or {@code "Recipe"}). Within a group, output ingredients
 * come first and input ingredients follow, and the same ingredient appearing
 * in multiple slots of the recipe is merged into a single entry with the
 * amount summed across all slots (the 1.21.1 {@code mergeRecipeInputs}
 * semantics). Amounts are resolved via
 * {@link BookmarkIngredientAmountResolver}.
 *
 * <p>The resolver is intentionally decoupled from {@code IRecipeLayoutDrawable}
 * so it can be unit tested without a client runtime.
 */
public final class FavoriteTreeBookmarkEntryResolver {
	private FavoriteTreeBookmarkEntryResolver() {
	}

	/**
	 * Resolves the given slot ingredients (in tree order) into one group per
	 * recipe. Group order follows the input order; entries of the same recipe
	 * are always contiguous within their group.
	 */
	public static List<RecipeTreeBookmarkGroup> resolve(
		List<RecipeTreeSlotIngredient> slots,
		IIngredientManager ingredientManager
	) {
		if (slots.isEmpty()) {
			return List.of();
		}
		Map<ResourceLocation, List<RecipeTreeSlotIngredient>> slotsByRecipe = new LinkedHashMap<>();
		for (RecipeTreeSlotIngredient slot : slots) {
			slotsByRecipe.computeIfAbsent(slot.recipeUid(), ignored -> new ArrayList<>()).add(slot);
		}
		List<RecipeTreeBookmarkGroup> groups = new ArrayList<>(slotsByRecipe.size());
		for (Map.Entry<ResourceLocation, List<RecipeTreeSlotIngredient>> entry : slotsByRecipe.entrySet()) {
			List<RecipeTreeSlotIngredient> recipeSlots = entry.getValue();
			String title = getGroupTitle(recipeSlots, ingredientManager);
			List<RecipeTreeBookmarkEntry> entries = resolveRecipeEntries(recipeSlots, ingredientManager);
			if (!entries.isEmpty()) {
				groups.add(new RecipeTreeBookmarkGroup(title, entries));
			}
		}
		return groups;
	}

	/**
	 * Mirrors the 1.21.1 {@code BookmarkList#getRecipeBookmarkGroupTitle}:
	 * the display name of the first output ingredient, falling back to the
	 * first input ingredient, or {@code "Recipe"}.
	 */
	public static String getGroupTitle(
		List<RecipeTreeSlotIngredient> recipeSlots,
		IIngredientManager ingredientManager
	) {
		return findFirstIngredient(recipeSlots, RecipeIngredientRole.OUTPUT)
			.or(() -> findFirstIngredient(recipeSlots, RecipeIngredientRole.INPUT))
			.map(ingredient -> getIngredientDisplayName(ingredient, ingredientManager))
			.orElse("Recipe");
	}

	private static Optional<ITypedIngredient<?>> findFirstIngredient(
		List<RecipeTreeSlotIngredient> recipeSlots,
		RecipeIngredientRole role
	) {
		for (RecipeTreeSlotIngredient slot : recipeSlots) {
			if (slot.role() == role) {
				return Optional.of(slot.ingredient());
			}
		}
		return Optional.empty();
	}

	private static List<RecipeTreeBookmarkEntry> resolveRecipeEntries(
		List<RecipeTreeSlotIngredient> recipeSlots,
		IIngredientManager ingredientManager
	) {
		Map<IngredientMergeKey, MergedEntry> merged = new LinkedHashMap<>();
		mergeRoleSlots(merged, recipeSlots, RecipeIngredientRole.OUTPUT, ingredientManager);
		mergeRoleSlots(merged, recipeSlots, RecipeIngredientRole.INPUT, ingredientManager);
		return merged.values()
			.stream()
			.map(MergedEntry::toEntry)
			.toList();
	}

	private static void mergeRoleSlots(
		Map<IngredientMergeKey, MergedEntry> merged,
		List<RecipeTreeSlotIngredient> recipeSlots,
		RecipeIngredientRole role,
		IIngredientManager ingredientManager
	) {
		for (RecipeTreeSlotIngredient slot : recipeSlots) {
			if (slot.role() != role) {
				continue;
			}
			ITypedIngredient<?> ingredient = slot.ingredient();
			IIngredientHelper<Object> helper = castHelper(ingredientManager.getIngredientHelper(ingredient.getType()));
			String uniqueId = helper.getUniqueId(ingredient.getIngredient(), UidContext.Ingredient);
			// The merge key includes the role so that the same ingredient
			// appearing in both OUTPUT and INPUT slots yields two entries,
			// mirroring the 1.21.1 RecipeBookmark.equals which compares
			// displayRole (BookmarkList#addSlotBookmarks de-duplication).
			IngredientMergeKey key = new IngredientMergeKey(ingredient.getType().getUid(), uniqueId, role);
			long amount = BookmarkIngredientAmountResolver.getAmount(ingredient, ingredientManager);
			MergedEntry existing = merged.get(key);
			if (existing == null) {
				merged.put(key, new MergedEntry(slot.recipeUid(), ingredient, role, amount));
			} else {
				existing.add(amount);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static IIngredientHelper<Object> castHelper(IIngredientHelper<?> helper) {
		return (IIngredientHelper<Object>) helper;
	}

	private static <T> String getIngredientDisplayName(
		ITypedIngredient<T> ingredient,
		IIngredientManager ingredientManager
	) {
		IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(ingredient.getType());
		return ingredientHelper.getDisplayName(ingredient.getIngredient());
	}

	private record IngredientMergeKey(String ingredientTypeUid, String ingredientUid, RecipeIngredientRole role) {
	}

	private static final class MergedEntry {
		private final ResourceLocation recipeUid;
		private final ITypedIngredient<?> ingredient;
		private final RecipeIngredientRole role;
		private long amount;

		private MergedEntry(ResourceLocation recipeUid, ITypedIngredient<?> ingredient, RecipeIngredientRole role, long amount) {
			this.recipeUid = recipeUid;
			this.ingredient = ingredient;
			this.role = role;
			this.amount = amount;
		}

		public void add(long amount) {
			// Saturated addition mirrors the 1.21.1 SaturatedMath.add used by
			// BookmarkItemMetadataFactory#getMatchedFactor.
			this.amount = saturatedAdd(this.amount, amount);
		}

		private static long saturatedAdd(long first, long second) {
			try {
				return Math.addExact(first, second);
			} catch (ArithmeticException e) {
				return Long.MAX_VALUE;
			}
		}

		public RecipeTreeBookmarkEntry toEntry() {
			return new RecipeTreeBookmarkEntry(recipeUid, ingredient, role, amount);
		}
	}
}

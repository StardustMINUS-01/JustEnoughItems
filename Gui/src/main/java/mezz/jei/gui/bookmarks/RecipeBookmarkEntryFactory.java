package mezz.jei.gui.bookmarks;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.util.SaturatedMath;
import mezz.jei.gui.compat.gtm.GtmVirtualCircuitCompat;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class RecipeBookmarkEntryFactory {
	private final IIngredientManager ingredientManager;

	RecipeBookmarkEntryFactory(IIngredientManager ingredientManager) {
		this.ingredientManager = ingredientManager;
	}

	List<RecipeBookmarkEntry> createRecipeBookmarkEntries(
		RecipeLayoutProjection projection,
		boolean preserveAmount,
		Object equalityScope
	) {
		IRecipeLayoutDrawable<?> recipeLayout = projection.layout();
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs = GtmVirtualCircuitCompat.projectVirtualInputs(recipeLayout.getRecipe(), ingredientManager);
		List<RecipeBookmarkEntry> bookmarks = new ArrayList<>();
		addSlotBookmarks(bookmarks, recipeLayout, RecipeIngredientRole.OUTPUT, preserveAmount, projection, virtualInputs, equalityScope);
		addSlotBookmarks(bookmarks, recipeLayout, RecipeIngredientRole.INPUT, preserveAmount, projection, virtualInputs, equalityScope);
		addSyntheticNonConsumableBookmarks(bookmarks, recipeLayout, preserveAmount, virtualInputs, equalityScope);
		return bookmarks;
	}

	<R> Optional<RecipeBookmarkEntry> createPrimaryRecipeBookmarkEntry(IRecipeLayoutDrawable<R> recipeLayout, boolean preserveAmount) {
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs = GtmVirtualCircuitCompat.projectVirtualInputs(recipeLayout.getRecipe(), ingredientManager);
		IRecipeSlotsView recipeSlotsView = recipeLayout.getRecipeSlotsView();
		Optional<RecipeBookmarkEntry> output = createFirstSlotBookmark(recipeLayout, recipeSlotsView, RecipeIngredientRole.OUTPUT, preserveAmount, virtualInputs);
		if (output.isPresent()) {
			return output;
		}
		return createFirstSlotBookmark(recipeLayout, recipeSlotsView, RecipeIngredientRole.INPUT, preserveAmount, virtualInputs);
	}

	private <R> Optional<RecipeBookmarkEntry> createFirstSlotBookmark(
		IRecipeLayoutDrawable<R> recipeLayout,
		IRecipeSlotsView recipeSlotsView,
		RecipeIngredientRole role,
		boolean preserveAmount,
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs
	) {
		List<IRecipeSlotView> roleSlots = recipeSlotsView.getSlotViews(role);
		for (IRecipeSlotView slotView : roleSlots) {
			Optional<ITypedIngredient<?>> ingredient = slotView.getAllIngredients().findFirst();
			if (ingredient.isPresent()) {
				return Optional.of(createRecipeBookmark(recipeLayout, slotView, roleSlots, ingredient.get(), role, preserveAmount, virtualInputs, null, null));
			}
		}
		return Optional.empty();
	}

	private <R> void addSlotBookmarks(
		List<RecipeBookmarkEntry> bookmarks,
		IRecipeLayoutDrawable<R> recipeLayout,
		RecipeIngredientRole role,
		boolean preserveAmount,
		RecipeLayoutProjection projection,
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs,
		Object equalityScope
	) {
		IRecipeSlotsView recipeSlotsView = role == RecipeIngredientRole.INPUT ?
			projection.getRecipeSlotsView() :
			recipeLayout.getRecipeSlotsView();
		List<IRecipeSlotView> roleSlots = recipeSlotsView.getSlotViews(role);
		if (role != RecipeIngredientRole.INPUT) {
			for (IRecipeSlotView slotView : roleSlots) {
				Optional<ITypedIngredient<?>> ingredient;
				if (role == RecipeIngredientRole.OUTPUT && projection.selectedOutputKey().isPresent()) {
					ingredient = getSelectedIngredient(slotView, projection.selectedOutputKey().get());
				} else {
					ingredient = slotView.getAllIngredients().findFirst();
				}
				ingredient
					.map(selected -> createRecipeBookmark(recipeLayout, slotView, roleSlots, selected, role, preserveAmount, virtualInputs, equalityScope, null))
					.ifPresent(entry -> addBookmarkIfAbsent(bookmarks, entry));
			}
			return;
		}
		List<Optional<ITypedIngredient<?>>> selectedIngredients = new ArrayList<>(roleSlots.size());
		Map<BookmarkIngredientKey, Long> selectedInputFactors = new HashMap<>();
		for (int i = 0; i < roleSlots.size(); i++) {
			IRecipeSlotView slotView = roleSlots.get(i);
			Optional<ITypedIngredient<?>> ingredient = projection.selectedInputKey(i)
				.flatMap(key -> getSelectedIngredient(slotView, key))
				.or(() -> slotView.getAllIngredients().findFirst());
			selectedIngredients.add(ingredient);
			ingredient.ifPresent(selected -> selectedInputFactors.merge(
				BookmarkItemMetadataFactory.createPermutationKey(selected, ingredientManager),
				BookmarkIngredientAmountResolver.getAmount(selected, ingredientManager),
				SaturatedMath::add
			));
		}
		for (int i = 0; i < roleSlots.size(); i++) {
			IRecipeSlotView slotView = roleSlots.get(i);
			Optional<ITypedIngredient<?>> ingredient = selectedIngredients.get(i);
			BookmarkIngredientKey lockedInputPermutation = projection.selectedInputKey(i).orElse(null);
			ingredient
				.map(selected -> {
					BookmarkIngredientKey selectedKey = BookmarkItemMetadataFactory.createPermutationKey(selected, ingredientManager);
					long factor = selectedInputFactors.get(selectedKey);
					return createRecipeBookmarkWithFactor(
						recipeLayout,
						slotView,
						selected,
						role,
						preserveAmount,
						virtualInputs,
						equalityScope,
						lockedInputPermutation,
						factor
					);
				})
				.ifPresent(entry -> addBookmarkIfAbsent(bookmarks, entry));
		}
	}

	private static void addBookmarkIfAbsent(List<RecipeBookmarkEntry> bookmarks, RecipeBookmarkEntry entry) {
		if (bookmarks.stream().noneMatch(bookmark -> bookmark.bookmark().equals(entry.bookmark()))) {
			bookmarks.add(entry);
		}
	}

	private <R> void addSyntheticNonConsumableBookmarks(
		List<RecipeBookmarkEntry> bookmarks,
		IRecipeLayoutDrawable<R> recipeLayout,
		boolean preserveAmount,
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs,
		Object equalityScope
	) {
		List<IRecipeSlotView> inputSlots = recipeLayout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT);
		for (GtmVirtualCircuitCompat.VirtualInput virtualInput : virtualInputs.inputs()) {
			ITypedIngredient<?> ingredient = virtualInput.ingredient();
			boolean displayed = inputSlots.stream()
				.flatMap(IRecipeSlotView::getAllIngredients)
				.anyMatch(candidate -> sameIngredient(candidate, ingredient));
			if (displayed) {
				continue;
			}
			RecipeBookmarkEntry entry = createSyntheticRecipeInputBookmark(recipeLayout, virtualInput, preserveAmount, equalityScope);
			addBookmarkIfAbsent(bookmarks, entry);
		}
	}

	private <R, T> RecipeBookmarkEntry createSyntheticRecipeInputBookmark(
		IRecipeLayoutDrawable<R> recipeLayout,
		GtmVirtualCircuitCompat.VirtualInput virtualInput,
		boolean preserveAmount,
		Object equalityScope
	) {
		@SuppressWarnings("unchecked")
		ITypedIngredient<T> ingredient = (ITypedIngredient<T>) virtualInput.ingredient();
		IRecipeCategory<R> recipeCategory = recipeLayout.getRecipeCategory();
		R recipe = recipeLayout.getRecipe();
		ResourceLocation recipeUid = recipeCategory.getRegistryName(recipe);
		if (recipeUid == null) {
			IBookmark bookmark = preserveAmount ?
				IngredientBookmark.createPreservingAmount(ingredient, ingredientManager) :
				IngredientBookmark.create(ingredient, ingredientManager);
			return new RecipeBookmarkEntry(bookmark, BookmarkItemMetadata.defaultForGroup(BookmarkGroupManager.DEFAULT_GROUP_ID));
		}
		ITypedIngredient<T> bookmarkIngredient = preserveAmount ? ingredient : ingredientManager.normalizeTypedIngredient(ingredient);
		IBookmark bookmark = new RecipeBookmark<>(recipeCategory, recipe, recipeUid, bookmarkIngredient, RecipeIngredientRole.INPUT, equalityScope);
		long factor = virtualInput.programmedCircuit() ?
			0 : BookmarkIngredientAmountResolver.getAmount(ingredient, ingredientManager);
		BookmarkItemMetadata metadata = BookmarkItemMetadataFactory.createForSyntheticRecipeInput(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			recipeCategory.getRecipeType().getUid(),
			recipeUid,
			BookmarkItemType.NONCONSUMABLE,
			ingredient,
			ingredientManager,
			factor
		);
		return new RecipeBookmarkEntry(bookmark, metadata);
	}

	private Optional<ITypedIngredient<?>> getSelectedIngredient(
		IRecipeSlotView slotView,
		BookmarkIngredientKey selectedKey
	) {
		if (selectedKey == null || ingredientManager == null) {
			return Optional.empty();
		}
		return slotView.getAllIngredients()
			.filter(ingredient -> selectedKey.equals(BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager)))
			.findFirst();
	}

	private <R, T> RecipeBookmarkEntry createRecipeBookmark(
		IRecipeLayoutDrawable<R> recipeLayout,
		IRecipeSlotView slotView,
		List<IRecipeSlotView> roleSlots,
		ITypedIngredient<T> ingredient,
		RecipeIngredientRole role,
		boolean preserveAmount,
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs,
		Object equalityScope,
		@Nullable BookmarkIngredientKey lockedInputPermutation
	) {
		long factor = BookmarkItemMetadataFactory.getMatchedFactor(ingredient, roleSlots, ingredientManager);
		return createRecipeBookmarkWithFactor(
			recipeLayout,
			slotView,
			ingredient,
			role,
			preserveAmount,
			virtualInputs,
			equalityScope,
			lockedInputPermutation,
			factor
		);
	}

	private <R, T> RecipeBookmarkEntry createRecipeBookmarkWithFactor(
		IRecipeLayoutDrawable<R> recipeLayout,
		IRecipeSlotView slotView,
		ITypedIngredient<T> ingredient,
		RecipeIngredientRole role,
		boolean preserveAmount,
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs,
		Object equalityScope,
		@Nullable BookmarkIngredientKey lockedInputPermutation,
		long factor
	) {
		IRecipeCategory<R> recipeCategory = recipeLayout.getRecipeCategory();
		R recipe = recipeLayout.getRecipe();
		ITypedIngredient<T> bookmarkIngredient = preserveAmount ? ingredient : ingredientManager.normalizeTypedIngredient(ingredient);
		ResourceLocation recipeUid = recipeCategory.getRegistryName(recipe);
		if (recipeUid == null) {
			IBookmark bookmark = preserveAmount ?
				IngredientBookmark.createPreservingAmount(ingredient, ingredientManager) :
				IngredientBookmark.create(ingredient, ingredientManager);
			return new RecipeBookmarkEntry(bookmark, BookmarkItemMetadata.defaultForGroup(BookmarkGroupManager.DEFAULT_GROUP_ID));
		}
		IBookmark bookmark = new RecipeBookmark<>(recipeCategory, recipe, recipeUid, bookmarkIngredient, role, equalityScope);
		boolean virtualInput = role == RecipeIngredientRole.INPUT && virtualInputs.inputs().stream()
			.map(GtmVirtualCircuitCompat.VirtualInput::ingredient)
			.anyMatch(candidate -> sameIngredient(ingredient, candidate));
		BookmarkItemType type = virtualInput ?
			BookmarkItemType.NONCONSUMABLE : BookmarkItemType.fromRecipeRole(role);
		BookmarkItemMetadata metadata = BookmarkItemMetadataFactory.createForRecipeSlotWithFactor(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			recipeCategory,
			recipeUid,
			type,
			slotView,
			ingredient,
			ingredientManager,
			factor
		);
		if (lockedInputPermutation != null) {
			metadata = metadata.withPermutations(Set.of(lockedInputPermutation));
		}
		return new RecipeBookmarkEntry(bookmark, metadata);
	}

	private boolean sameIngredient(ITypedIngredient<?> first, ITypedIngredient<?> second) {
		return BookmarkItemMetadataFactory.createPermutationKey(first, ingredientManager)
			.equals(BookmarkItemMetadataFactory.createPermutationKey(second, ingredientManager));
	}

	String getRecipeBookmarkGroupTitle(RecipeLayoutProjection projection) {
		IRecipeSlotsView recipeSlotsView = projection.getRecipeSlotsView();
		return findSelectedOutputIngredient(recipeSlotsView, projection.selectedOutputKey())
			.or(() -> findFirstIngredient(recipeSlotsView, RecipeIngredientRole.OUTPUT))
			.or(() -> findFirstIngredient(recipeSlotsView, RecipeIngredientRole.INPUT))
			.map(this::getIngredientDisplayName)
			.orElse("Recipe");
	}

	private Optional<ITypedIngredient<?>> findSelectedOutputIngredient(
		IRecipeSlotsView recipeSlotsView,
		Optional<BookmarkIngredientKey> selectedOutputKey
	) {
		if (selectedOutputKey.isEmpty() || ingredientManager == null) {
			return Optional.empty();
		}
		return recipeSlotsView.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
			.map(slot -> slot.getAllIngredients()
				.filter(ingredient -> selectedOutputKey.get().equals(BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager)))
				.findFirst())
			.flatMap(Optional::stream)
			.findFirst();
	}

	private Optional<ITypedIngredient<?>> findFirstIngredient(IRecipeSlotsView recipeSlotsView, RecipeIngredientRole role) {
		return recipeSlotsView.getSlotViews(role).stream()
			.map(slot -> slot.getAllIngredients().findFirst())
			.flatMap(Optional::stream)
			.findFirst();
	}

	private <T> String getIngredientDisplayName(ITypedIngredient<T> ingredient) {
		IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(ingredient.getType());
		return ingredientHelper.getDisplayName(ingredient.getIngredient());
	}

	record RecipeBookmarkEntry(IBookmark bookmark, BookmarkItemMetadata metadata) {
	}
}

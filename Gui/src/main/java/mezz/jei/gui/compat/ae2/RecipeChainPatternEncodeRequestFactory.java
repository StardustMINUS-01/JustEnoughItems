package mezz.jei.gui.compat.ae2;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import mezz.jei.gui.compat.gtm.GtmVirtualCircuitCompat;
import mezz.jei.common.platform.IPlatformFluidHelperInternal;
import mezz.jei.common.platform.Services;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class RecipeChainPatternEncodeRequestFactory {
	public static final int MAX_REQUESTS = 256;

	private final IIngredientManager ingredientManager;

	public RecipeChainPatternEncodeRequestFactory(IIngredientManager ingredientManager) {
		this.ingredientManager = ingredientManager;
	}

	public Result createRequests(
		List<RecipeChainInput> chainInputs,
		Set<ResourceLocation> collapsedRecipeIds,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> layoutResolver
	) {
		LinkedHashSet<ResourceLocation> recipeOrder = getActiveRecipeOrder(chainInputs, collapsedRecipeIds);
		List<JeiPatternEncodeRequest> requests = new ArrayList<>();
		for (ResourceLocation recipeUid : recipeOrder) {
			if (requests.size() >= MAX_REQUESTS) {
				return new Result(Status.TOO_MANY_REQUESTS, List.of());
			}
			findTargetResult(chainInputs, recipeUid)
				.flatMap(target -> createBookmarkRequest(target, getRecipeInputs(chainInputs, recipeUid), layoutResolver))
				.ifPresent(requests::add);
		}
		if (requests.isEmpty()) {
			return new Result(Status.EMPTY, List.of());
		}
		return new Result(Status.OK, requests);
	}

	public Result createSingleRequest(IRecipeLayoutDrawable<?> layout, Optional<IRecipeSlotView> hoveredSlot) {
		return createSingleRequest(layout, hoveredSlot, List.of());
	}

	public Result createSingleRequest(
		IRecipeLayoutDrawable<?> layout,
		Optional<IRecipeSlotView> hoveredSlot,
		List<RecipeChainInput> bookmarkInputs
	) {
		ResourceLocation recipeUid = getRecipeUid(layout);
		if (recipeUid == null) {
			return new Result(Status.EMPTY, List.of());
		}
		ResourceLocation recipeTypeUid = layout.getRecipeCategory().getRecipeType().getUid();
		Optional<JeiPatternEncodeRequest> request = getCanonicalMode(recipeTypeUid)
			.flatMap(mode -> createCanonicalRequest(recipeTypeUid, recipeUid, mode, layout.getRecipeSlotsView(), bookmarkInputs))
			.or(() -> bookmarkInputs.isEmpty() ?
				createProcessingRequestFromLayout(hoveredSlot, recipeTypeUid, recipeUid, layout.getRecipe(), layout.getRecipeSlotsView()) :
				createProcessingRequestFromBookmarks(getHoveredOutputKeys(hoveredSlot), recipeTypeUid, recipeUid, bookmarkInputs));
		return request
			.map(r -> new Result(Status.OK, List.of(r)))
			.orElseGet(() -> new Result(Status.EMPTY, List.of()));
	}

	private static <T> @Nullable ResourceLocation getRecipeUid(IRecipeLayoutDrawable<T> layout) {
		return layout.getRecipeCategory().getRegistryName(layout.getRecipe());
	}

	private LinkedHashSet<ResourceLocation> getActiveRecipeOrder(List<RecipeChainInput> chainInputs, Set<ResourceLocation> collapsedRecipeIds) {
		RecipeChainDetails details = RecipeChainMath.refresh(chainInputs, collapsedRecipeIds);
		Set<ResourceLocation> activeRecipes = new LinkedHashSet<>();
		activeRecipes.addAll(details.outputRecipes());
		activeRecipes.addAll(details.middleRecipes());
		if (activeRecipes.isEmpty()) {
			for (RecipeChainInput input : chainInputs) {
				BookmarkItemMetadata metadata = input.metadata();
				if (metadata.type().isGraphOutput() && metadata.recipeUid() != null) {
					activeRecipes.add(metadata.recipeUid());
				}
			}
		}

		LinkedHashSet<ResourceLocation> ordered = new LinkedHashSet<>();
		for (RecipeChainInput input : chainInputs) {
			BookmarkItemMetadata metadata = input.metadata();
			ResourceLocation recipeUid = metadata.recipeUid();
			if (metadata.type().isGraphOutput() && recipeUid != null && activeRecipes.contains(recipeUid)) {
				ordered.add(recipeUid);
			}
		}
		return ordered;
	}

	private static Optional<RecipeChainInput> findTargetResult(List<RecipeChainInput> chainInputs, ResourceLocation recipeUid) {
		return chainInputs.stream()
			.filter(input -> input.metadata().type().isGraphOutput())
			.filter(input -> recipeUid.equals(input.metadata().recipeUid()))
			.findFirst();
	}

	private static List<RecipeChainInput> getRecipeInputs(List<RecipeChainInput> chainInputs, ResourceLocation recipeUid) {
		return chainInputs.stream()
			.filter(input -> recipeUid.equals(input.metadata().recipeUid()))
			.toList();
	}

	private Optional<JeiPatternEncodeRequest> createBookmarkRequest(
		RecipeChainInput target,
		List<RecipeChainInput> bookmarkInputs,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> layoutResolver
	) {
		ResourceLocation recipeUid = target.metadata().recipeUid();
		ResourceLocation recipeTypeUid = target.metadata().recipeTypeUid();
		if (recipeUid == null || recipeTypeUid == null) {
			return Optional.empty();
		}
		Optional<JeiPatternEncodeMode> canonicalMode = getCanonicalMode(recipeTypeUid);
		if (canonicalMode.isPresent()) {
			return layoutResolver.apply(recipeUid)
				.flatMap(layout -> createCanonicalRequest(recipeTypeUid, recipeUid, canonicalMode.get(), layout.getRecipeSlotsView(), bookmarkInputs));
		}
		return createProcessingRequestFromBookmarks(
			Optional.of(target)
				.map(RecipeChainPatternEncodeRequestFactory::getTargetKeys),
			recipeTypeUid,
			recipeUid,
			bookmarkInputs
		);
	}

	private static Optional<JeiPatternEncodeMode> getCanonicalMode(ResourceLocation recipeTypeUid) {
		if (RecipeTypes.CRAFTING.getUid().equals(recipeTypeUid)) {
			return Optional.of(JeiPatternEncodeMode.CRAFTING);
		}
		if (RecipeTypes.STONECUTTING.getUid().equals(recipeTypeUid)) {
			return Optional.of(JeiPatternEncodeMode.STONECUTTING);
		}
		if (RecipeTypes.SMITHING.getUid().equals(recipeTypeUid)) {
			return Optional.of(JeiPatternEncodeMode.SMITHING_TABLE);
		}
		return Optional.empty();
	}

	private Optional<JeiPatternEncodeRequest> createCanonicalRequest(
		ResourceLocation recipeTypeUid,
		ResourceLocation recipeUid,
		JeiPatternEncodeMode mode,
		IRecipeSlotsView slotsView,
		List<RecipeChainInput> bookmarkInputs
	) {
		Optional<List<@Nullable JeiPatternStack>> guides = readCanonicalInputGuides(mode, slotsView, bookmarkInputs);
		if (guides.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new JeiPatternEncodeRequest(
			recipeTypeUid,
			recipeUid,
			mode,
			List.of(),
			List.of(),
			List.of(),
			guides.get(),
			recipeUid,
			false,
			false
		));
	}

	private Optional<List<@Nullable JeiPatternStack>> readCanonicalInputGuides(
		JeiPatternEncodeMode mode,
		IRecipeSlotsView slotsView,
		List<RecipeChainInput> bookmarkInputs
	) {
		List<IRecipeSlotView> inputSlots = slotsView.getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.INPUT);
		int expectedSlots = switch (mode) {
			case CRAFTING -> 9;
			case STONECUTTING -> 1;
			case SMITHING_TABLE -> 3;
			case PROCESSING -> 0;
		};
		if (inputSlots.size() != expectedSlots) {
			return Optional.empty();
		}
		if (!bookmarkInputs.isEmpty()) {
			return readBookmarkedCanonicalInputGuides(inputSlots, bookmarkInputs);
		}
		List<@Nullable JeiPatternStack> guides = new ArrayList<>(expectedSlots);
		for (IRecipeSlotView slot : inputSlots) {
			Optional<ITypedIngredient<?>> ingredient = slot.getDisplayedIngredient().or(() -> slot.getAllIngredients().findFirst());
			if (ingredient.isEmpty()) {
				guides.add(null);
				continue;
			}
			Optional<JeiPatternStack> guide = toCanonicalGuide(ingredient.get());
			if (guide.isEmpty()) {
				return Optional.empty();
			}
			guides.add(guide.get());
		}
		return Optional.of(Collections.unmodifiableList(new ArrayList<>(guides)));
	}

	private Optional<List<@Nullable JeiPatternStack>> readBookmarkedCanonicalInputGuides(
		List<IRecipeSlotView> inputSlots,
		List<RecipeChainInput> bookmarkInputs
	) {
		java.util.Map<Set<BookmarkIngredientKey>, List<BookmarkedCanonicalGuide>> guidesByFamily = new java.util.LinkedHashMap<>();
		for (RecipeChainInput input : bookmarkInputs) {
			BookmarkItemMetadata metadata = input.metadata();
			BookmarkIngredientKey selectedKey = input.selectedKey();
			if (
				!metadata.type().isGraphInput() ||
				selectedKey == null ||
				metadata.permutations().isEmpty() ||
				metadata.factor() <= 0
			) {
				continue;
			}
			guidesByFamily.computeIfAbsent(metadata.permutations(), ignored -> new ArrayList<>())
				.add(new BookmarkedCanonicalGuide(selectedKey, metadata.factor()));
		}

		List<@Nullable JeiPatternStack> guides = new ArrayList<>(inputSlots.size());
		for (IRecipeSlotView slot : inputSlots) {
			Set<BookmarkIngredientKey> slotPermutations = getPermutationKeys(slot);
			Optional<ITypedIngredient<?>> selected = takeBookmarkedCanonicalGuide(slot, guidesByFamily.get(slotPermutations))
				.or(() -> slot.getDisplayedIngredient().or(() -> slot.getAllIngredients().findFirst()));
			if (selected.isEmpty()) {
				guides.add(null);
				continue;
			}
			Optional<JeiPatternStack> guide = toCanonicalGuide(selected.get());
			if (guide.isEmpty()) {
				return Optional.empty();
			}
			guides.add(guide.get());
		}
		return Optional.of(Collections.unmodifiableList(new ArrayList<>(guides)));
	}

	private Set<BookmarkIngredientKey> getPermutationKeys(IRecipeSlotView slot) {
		Set<BookmarkIngredientKey> permutations = new java.util.LinkedHashSet<>();
		slot.getAllIngredients()
			.map(ingredient -> BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager))
			.forEach(permutations::add);
		return permutations;
	}

	private Optional<ITypedIngredient<?>> takeBookmarkedCanonicalGuide(
		IRecipeSlotView slot,
		@Nullable List<BookmarkedCanonicalGuide> guides
	) {
		if (guides == null) {
			return Optional.empty();
		}
		for (BookmarkedCanonicalGuide guide : guides) {
			if (guide.remaining() <= 0) {
				continue;
			}
			Optional<ITypedIngredient<?>> selected = slot.getAllIngredients()
				.filter(candidate -> guide.key().equals(BookmarkItemMetadataFactory.createPermutationKey(candidate, ingredientManager)))
				.findFirst();
			if (selected.isPresent()) {
				guide.consume();
				return selected;
			}
		}
		return Optional.empty();
	}

	private static final class BookmarkedCanonicalGuide {
		private final BookmarkIngredientKey key;
		private long remaining;

		private BookmarkedCanonicalGuide(BookmarkIngredientKey key, long remaining) {
			this.key = key;
			this.remaining = remaining;
		}

		private BookmarkIngredientKey key() {
			return key;
		}

		private long remaining() {
			return remaining;
		}

		private void consume() {
			remaining--;
		}
	}

	private static Optional<JeiPatternStack> toCanonicalGuide(ITypedIngredient<?> ingredient) {
		return ingredient.getItemStack()
			.filter(stack -> !stack.isEmpty())
			.map(stack -> new JeiPatternStack(JeiPatternStack.Kind.ITEM, ingredient, 1));
	}

	private Optional<JeiPatternEncodeRequest> createProcessingRequestFromLayout(
		Optional<IRecipeSlotView> hoveredSlot,
		ResourceLocation recipeTypeUid,
		ResourceLocation recipeUid,
		Object recipe,
		IRecipeSlotsView slotsView
	) {
		List<@Nullable JeiPatternStack> sparseInputs = readSlots(slotsView.getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.INPUT));
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs = GtmVirtualCircuitCompat.projectVirtualInputs(recipe, ingredientManager);
		List<@Nullable JeiPatternStack> sparseOutputs = readSlots(slotsView.getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT));
		return createProcessingRequest(
			getHoveredOutputKeys(hoveredSlot),
			recipeTypeUid,
			recipeUid,
			sparseInputs,
			sparseOutputs,
			projectGtmCatalysts(virtualInputs, sparseInputs)
		);
	}

	private Optional<JeiPatternEncodeRequest> createProcessingRequestFromBookmarks(
		Optional<Set<BookmarkIngredientKey>> primaryOutputKeys,
		ResourceLocation recipeTypeUid,
		ResourceLocation recipeUid,
		List<RecipeChainInput> bookmarkInputs
	) {
		List<@Nullable JeiPatternStack> sparseInputs = new ArrayList<>();
		List<@Nullable JeiPatternStack> sparseOutputs = new ArrayList<>();
		List<JeiPatternCatalyst> catalysts = new ArrayList<>();
		for (RecipeChainInput input : bookmarkInputs) {
			BookmarkItemMetadata metadata = input.metadata();
			Optional<JeiPatternStack> stack = toBookmarkedStack(input);
			if (stack.isEmpty()) {
				return Optional.empty();
			}
			switch (metadata.type()) {
				case INGREDIENT -> sparseInputs.add(stack.get());
				case CATALYST -> {
					int sourceSlot = sparseInputs.size();
					sparseInputs.add(stack.get());
					catalysts.add(new JeiPatternCatalyst(sourceSlot, stack.get()));
				}
				case RESULT -> sparseOutputs.add(stack.get());
				case ITEM -> {
				}
			}
		}
		return createProcessingRequest(primaryOutputKeys, recipeTypeUid, recipeUid, sparseInputs, sparseOutputs, List.copyOf(catalysts));
	}

	private Optional<JeiPatternEncodeRequest> createProcessingRequest(
		Optional<Set<BookmarkIngredientKey>> primaryOutputKeys,
		ResourceLocation recipeTypeUid,
		ResourceLocation recipeUid,
		List<@Nullable JeiPatternStack> sparseInputs,
		List<@Nullable JeiPatternStack> sparseOutputs,
		List<JeiPatternCatalyst> catalysts
	) {
		if (sparseInputs.stream().allMatch(stack -> stack == null) || sparseOutputs.stream().allMatch(stack -> stack == null)) {
			return Optional.empty();
		}
		List<@Nullable JeiPatternStack> orderedOutputs = moveTargetOutputFirst(primaryOutputKeys, sparseOutputs);
		if (orderedOutputs.isEmpty() || orderedOutputs.getFirst() == null) {
			return Optional.empty();
		}
		return Optional.of(new JeiPatternEncodeRequest(
			recipeTypeUid,
			recipeUid,
			JeiPatternEncodeMode.PROCESSING,
			sparseInputs,
			orderedOutputs,
			catalysts,
			List.of(),
			null,
			false,
			false
		));
	}

	private Optional<JeiPatternStack> toBookmarkedStack(RecipeChainInput input) {
		ITypedIngredient<?> ingredient = input.selectedIngredient();
		if (ingredient == null) {
			return Optional.empty();
		}
		return toStack(ingredient)
			.map(stack -> new JeiPatternStack(stack.kind(), stack.ingredient(), Math.max(1, input.metadata().amount())));
	}

	private List<JeiPatternCatalyst> projectGtmCatalysts(
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs,
		List<@Nullable JeiPatternStack> sparseInputs
	) {
		Set<Integer> matchedSlots = new java.util.HashSet<>();
		List<JeiPatternCatalyst> catalysts = new ArrayList<>();
		for (GtmVirtualCircuitCompat.VirtualInput virtualInput : virtualInputs.inputs()) {
			Optional<JeiPatternStack> expected = toStack(virtualInput.ingredient());
			if (expected.isEmpty()) {
				continue;
			}
			int matchingSlot = -1;
			for (int slot = 0; slot < sparseInputs.size(); slot++) {
				JeiPatternStack existing = sparseInputs.get(slot);
				if (existing != null && !matchedSlots.contains(slot) && sameStack(existing, expected.get())) {
					matchingSlot = slot;
					break;
				}
			}
			if (matchingSlot >= 0) {
				sparseInputs.set(matchingSlot, expected.get());
				matchedSlots.add(matchingSlot);
			} else {
				sparseInputs.add(expected.get());
				matchingSlot = sparseInputs.size() - 1;
			}
			JeiPatternStack input = sparseInputs.get(matchingSlot);
			if (input != null) {
				catalysts.add(new JeiPatternCatalyst(matchingSlot, input));
			}
		}
		return List.copyOf(catalysts);
	}

	private boolean sameStack(JeiPatternStack first, JeiPatternStack second) {
		if (first.kind() != second.kind() || first.amount() != second.amount()) {
			return false;
		}
		return sameIngredient(first, second);
	}

	private boolean sameIngredient(JeiPatternStack first, JeiPatternStack second) {
		if (first.kind() != second.kind()) {
			return false;
		}
		BookmarkIngredientKey firstKey = BookmarkItemMetadataFactory.createPermutationKey(first.ingredient(), ingredientManager);
		BookmarkIngredientKey secondKey = BookmarkItemMetadataFactory.createPermutationKey(second.ingredient(), ingredientManager);
		return firstKey.equals(secondKey);
	}

	private List<@Nullable JeiPatternStack> readSlots(List<IRecipeSlotView> slots) {
		List<@Nullable JeiPatternStack> stacks = new ArrayList<>(slots.size());
		for (IRecipeSlotView slot : slots) {
			Optional<ITypedIngredient<?>> ingredient = slot.getDisplayedIngredient()
				.or(() -> slot.getAllIngredients().findFirst());
			if (ingredient.isEmpty()) {
				stacks.add(null);
				continue;
			}
			Optional<JeiPatternStack> stack = toStack(ingredient.get());
			if (stack.isEmpty()) {
				return List.of();
			}
			stacks.add(stack.get());
		}
		return stacks;
	}

	private Optional<JeiPatternStack> toStack(ITypedIngredient<?> typedIngredient) {
		return typedIngredient.getItemStack()
			.filter(stack -> !stack.isEmpty())
			.map(stack -> new JeiPatternStack(JeiPatternStack.Kind.ITEM, typedIngredient, Math.max(1, stack.getCount())))
			.or(() -> toFluidStack(typedIngredient));
	}

	private Optional<JeiPatternStack> toFluidStack(ITypedIngredient<?> typedIngredient) {
		if (!"fluid_stack".equals(typedIngredient.getType().getUid())) {
			return Optional.empty();
		}
		return toFluidStack(typedIngredient, Services.PLATFORM.getFluidHelper());
	}

	private <T> Optional<JeiPatternStack> toFluidStack(ITypedIngredient<?> typedIngredient, IPlatformFluidHelperInternal<T> fluidHelper) {
		ITypedIngredient<T> fluidIngredient = typedIngredient.cast(fluidHelper.getFluidIngredientType());
		if (fluidIngredient == null) {
			return Optional.empty();
		}
		long amount = fluidHelper.getAmount(fluidIngredient.getIngredient());
		if (amount <= 0) {
			return Optional.empty();
		}
		return Optional.of(new JeiPatternStack(JeiPatternStack.Kind.FLUID, fluidIngredient, amount));
	}

	private List<@Nullable JeiPatternStack> moveTargetOutputFirst(Optional<Set<BookmarkIngredientKey>> targetKeys, List<@Nullable JeiPatternStack> sparseOutputs) {
		int targetIndex = findTargetOutputIndex(targetKeys, sparseOutputs);
		if (targetIndex <= 0) {
			return sparseOutputs;
		}
		List<@Nullable JeiPatternStack> ordered = new ArrayList<>(sparseOutputs.size());
		ordered.add(sparseOutputs.get(targetIndex));
		for (int i = 0; i < sparseOutputs.size(); i++) {
			if (i != targetIndex) {
				ordered.add(sparseOutputs.get(i));
			}
		}
		return ordered;
	}

	private int findTargetOutputIndex(Optional<Set<BookmarkIngredientKey>> targetKeys, List<@Nullable JeiPatternStack> sparseOutputs) {
		if (targetKeys.isEmpty() || targetKeys.get().isEmpty()) {
			return -1;
		}
		for (int i = 0; i < sparseOutputs.size(); i++) {
			JeiPatternStack stack = sparseOutputs.get(i);
			if (stack == null) {
				continue;
			}
			BookmarkIngredientKey key = BookmarkItemMetadataFactory.createPermutationKey(stack.ingredient(), ingredientManager);
			if (targetKeys.get().contains(key)) {
				return i;
			}
		}
		return -1;
	}

	private static Set<BookmarkIngredientKey> getTargetKeys(RecipeChainInput target) {
		return target.selectedKey() == null ?
			target.metadata().permutations() :
			Set.of(target.selectedKey());
	}

	private Optional<Set<BookmarkIngredientKey>> getHoveredOutputKeys(Optional<IRecipeSlotView> hoveredSlot) {
		return hoveredSlot
			.filter(slot -> slot.getRole() == mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT)
			.flatMap(slot -> slot.getDisplayedIngredient()
				.or(() -> slot.getAllIngredients().findFirst()))
			.map(ingredient -> Set.of(BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager)));
	}

	public enum Status {
		OK,
		EMPTY,
		TOO_MANY_REQUESTS
	}

	public record Result(Status status, List<JeiPatternEncodeRequest> requests) {
		public Result {
			requests = Collections.unmodifiableList(new ArrayList<>(requests));
		}
	}
}

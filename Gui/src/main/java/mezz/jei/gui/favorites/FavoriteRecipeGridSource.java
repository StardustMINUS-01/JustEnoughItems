package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientAmountResolver;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkCandidateTooltipState;
import mezz.jei.gui.bookmarks.BookmarkRowLayout;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource;
import mezz.jei.gui.overlay.bookmarks.BookmarkAmountFormatter;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.LayoutPlaceholderElement;
import mezz.jei.gui.recipes.FocusedRecipeLayoutResolver;
import mezz.jei.common.util.SaturatedMath;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public class FavoriteRecipeGridSource implements IIngredientGridSource {
	@FunctionalInterface
	public interface RecipeInputsResolver {
		ResolvedRecipeIngredients resolveIngredients(
			FocusedRecipe recipe,
			ITypedIngredient<?> target,
			Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> inputs
		);
	}

	public record ResolvedRecipeIngredients(Optional<ITypedIngredient<?>> target, List<ITypedIngredient<?>> inputs) {
		public ResolvedRecipeIngredients {
			target = target == null ? Optional.empty() : target;
			inputs = inputs == null ? List.of() : inputs;
		}

		public ResolvedRecipeIngredients(ITypedIngredient<?> target, List<ITypedIngredient<?>> inputs) {
			this(Optional.of(target), inputs);
		}
	}

	private final FavoriteRecipeStore store;
	private final FavoriteRecipePanelState panelState;
	private final IIngredientManager ingredientManager;
	private final IRecipeManager recipeManager;
	private final IFocusFactory focusFactory;
	private final RecipeInputsResolver recipeInputsResolver;
	private final BookmarkCandidateTooltipState permutationTooltipState = new BookmarkCandidateTooltipState();
	private List<IElement<?>> cachedGridElements;
	private final Map<RecipeRowsKey, List<IElement<?>>> cachedRecipeRows = new HashMap<>();

	public FavoriteRecipeGridSource(
		FavoriteRecipeStore store,
		FavoriteRecipePanelState panelState,
		IIngredientManager ingredientManager,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory
	) {
		this(
			store,
			panelState,
			ingredientManager,
			recipeManager,
			focusFactory,
			new LayoutRecipeInputsResolver(recipeManager, focusFactory, ingredientManager)
		);
	}

	public FavoriteRecipeGridSource(
		FavoriteRecipeStore store,
		FavoriteRecipePanelState panelState,
		IIngredientManager ingredientManager,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		RecipeInputsResolver recipeInputsResolver
	) {
		this.store = store;
		this.panelState = panelState;
		this.ingredientManager = ingredientManager;
		this.recipeManager = recipeManager;
		this.focusFactory = focusFactory;
		this.recipeInputsResolver = recipeInputsResolver;
	}

	@Override
	public List<IElement<?>> getElements() {
		if (cachedGridElements == null) {
			cachedGridElements = createElements();
		}
		return cachedGridElements;
	}

	@Override
	public List<IElement<?>> getElements(int columns) {
		return getElements(columns, List.of());
	}

	@Override
	public List<IElement<?>> getElements(int columns, List<Integer> usableColumnsPerRow) {
		if (panelState.displayMode() != FavoriteRecipePanelState.DisplayMode.RECIPE_ROWS || columns <= 1) {
			return getElements();
		}
		RecipeRowsKey key = new RecipeRowsKey(columns, List.copyOf(usableColumnsPerRow));
		return cachedRecipeRows.computeIfAbsent(key, this::createRecipeRows);
	}

	@Override
	public void addSourceListChangedListener(SourceListChangedListener listener) {
		store.addSourceListChangedListener(() -> {
			invalidateCache();
			listener.onSourceListChanged();
		});
		panelState.addDisplayStateChangedListener(() -> {
			invalidateCache();
			listener.onSourceListChanged();
		});
	}

	@Override
	public boolean isEmpty() {
		return store.isEmpty();
	}

	private void invalidateCache() {
		cachedGridElements = null;
		cachedRecipeRows.clear();
	}

	private List<IElement<?>> createRecipeRows(RecipeRowsKey key) {
		List<IElement<?>> rows = new ArrayList<>();
		BookmarkRowLayout.RowLayout rowLayout = BookmarkRowLayout.RowLayout.create(
			key.columns(),
			key.usableColumnsPerRow()
		);
		int position = 0;
		for (FavoriteRecipeStore.Entry entry : store.entries()) {
			position = createRecipeRow(entry, rowLayout, rows, position);
		}
		return List.copyOf(rows);
	}

	private List<IElement<?>> createElements() {
		return store.entries()
			.stream()
			.map(this::createElement)
			.flatMap(Optional::stream)
			.toList();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Optional<IElement<?>> createElement(FavoriteRecipeStore.Entry entry) {
		return resolveIngredient(entry.target())
			.map(ingredient -> createRecipeTargetElement((ITypedIngredient) ingredient, entry.recipe(), entry.inputs()))
			.map(element -> (IElement<?>) element);
	}

	private <T> FavoriteRecipeElement<T> createRecipeTargetElement(
		ITypedIngredient<T> displayIngredient,
		FocusedRecipe recipe,
		Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> entryInputs
	) {
		return new FavoriteRecipeElement<>(
			displayIngredient,
			displayIngredient,
			Optional.empty(),
			recipe,
			recipeManager,
			focusFactory,
			store,
			true,
			Optional.empty(),
			Optional.empty(),
			entryInputs,
			!panelState.isSortDragHidden(recipe, true, Optional.empty()),
			permutationTooltipState
		);
	}

	private <T> FavoriteRecipeElement<T> createRecipeTargetElement(
		ITypedIngredient<T> displayIngredient,
		ITypedIngredient<?> targetIngredient,
		FocusedRecipe recipe,
		Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> entryInputs
	) {
		return new FavoriteRecipeElement<>(
			displayIngredient,
			castIngredient(targetIngredient, displayIngredient),
			createAmountText(targetIngredient),
			recipe,
			recipeManager,
			focusFactory,
			store,
			true,
			Optional.empty(),
			Optional.empty(),
			entryInputs,
			!panelState.isSortDragHidden(recipe, true, Optional.empty()),
			permutationTooltipState
		);
	}

	private int createRecipeRow(
		FavoriteRecipeStore.Entry entry,
		BookmarkRowLayout.RowLayout rowLayout,
		List<IElement<?>> rows,
		int position
	) {
		Optional<ResolvedRecipeRowTarget> target = createRecipeRowTarget(entry);
		if (target.isEmpty()) {
			return position;
		}
		position = BookmarkRowLayout.rowStart(position, rowLayout);
		IElement<?> targetElement = target.get().element();
		rows.add(targetElement);
		position++;
		if (panelState.isRecipeRowCollapsed(entry.recipe())) {
			int rowEnd = BookmarkRowLayout.nextRowStart(position - 1, rowLayout);
			while (position < rowEnd) {
				rows.add(LayoutPlaceholderElement.INSTANCE);
				position++;
			}
			return position;
		}
		ResolvedRecipeIngredients resolvedIngredients = target.get().ingredients();
		List<IElement<?>> inputElements = new ArrayList<>();
		panelState.orderRecipeInputs(
				entry.recipe(),
				mergeRecipeInputs(resolvedIngredients.inputs()),
				MergedRecipeInput::key
			)
			.forEach(ingredient -> inputElements.add(createRecipeInputElement(ingredient, entry.recipe(), entry.inputs())));
		for (IElement<?> inputElement : inputElements) {
			rows.add(inputElement);
			position++;
		}
		while (!BookmarkRowLayout.isRowStart(position, rowLayout)) {
			rows.add(LayoutPlaceholderElement.INSTANCE);
			position++;
		}
		return position;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Optional<ResolvedRecipeRowTarget> createRecipeRowTarget(FavoriteRecipeStore.Entry entry) {
		return resolveIngredient(entry.target())
			.map(ingredient -> (ITypedIngredient) ingredient)
			.map(displayIngredient -> {
				ResolvedRecipeIngredients resolvedIngredients = recipeInputsResolver.resolveIngredients(entry.recipe(), displayIngredient, entry.inputs());
				ITypedIngredient<?> targetIngredient = resolvedIngredients.target()
					.orElse(displayIngredient);
				FavoriteRecipeElement<?> targetElement = createRecipeTargetElement(displayIngredient, targetIngredient, entry.recipe(), entry.inputs());
				return new ResolvedRecipeRowTarget(targetElement, resolvedIngredients);
			});
	}

	@SuppressWarnings({"removal", "rawtypes", "unchecked"})
	private Optional<ITypedIngredient<?>> resolveIngredient(BookmarkIngredientKey key) {
		if (key.typedIngredient() != null) {
			return Optional.of(key.typedIngredient());
		}
		return ingredientManager.getIngredientTypeForUid(key.ingredientTypeUid())
			.flatMap(type -> ingredientManager.getTypedIngredientByUid((IIngredientType) type, key.ingredientUid()))
			.map(ingredient -> (ITypedIngredient<?>) ingredient);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private FavoriteRecipeElement<?> createRecipeInputElement(
		MergedRecipeInput input,
		FocusedRecipe recipe,
		Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> slotInputs
	) {
		return createRecipeInputElement(
			(ITypedIngredient) input.ingredient(),
			input.amount(),
			recipe,
			input.key(),
			findSlotInput(slotInputs, input.key()),
			slotInputs
		);
	}

	private <T> FavoriteRecipeElement<T> createRecipeInputElement(
		ITypedIngredient<T> ingredient,
		long amount,
		FocusedRecipe recipe,
		FavoriteRecipePanelState.RecipeInputKey inputKey,
		@Nullable FavoriteRecipeStore.FavoriteSlotInput slotInput,
		Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> entryInputs
	) {
		ITypedIngredient<T> displayIngredient = ingredientManager.normalizeTypedIngredient(ingredient);
		return new FavoriteRecipeElement<>(
			displayIngredient,
			ingredient,
			createAmountText(ingredient, amount),
			recipe,
			recipeManager,
			focusFactory,
			store,
			false,
			Optional.of(inputKey),
			Optional.ofNullable(slotInput),
			entryInputs,
			!panelState.isSortDragHidden(recipe, false, Optional.of(inputKey)),
			permutationTooltipState
		);
	}

	@Nullable
	private static FavoriteRecipeStore.FavoriteSlotInput findSlotInput(
		Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> slotInputs,
		FavoriteRecipePanelState.RecipeInputKey inputKey
	) {
		for (FavoriteRecipeStore.FavoriteSlotInput slotInput : slotInputs.values()) {
			BookmarkIngredientKey selected = slotInput.selected();
			if (selected.ingredientTypeUid().equals(inputKey.ingredientTypeUid()) &&
				selected.ingredientUid().equals(inputKey.ingredientUid())) {
				return slotInput;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static <T> ITypedIngredient<T> castIngredient(ITypedIngredient<?> ingredient, ITypedIngredient<T> fallback) {
		if (ingredient.getType().equals(fallback.getType())) {
			return (ITypedIngredient<T>) ingredient;
		}
		return fallback;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Optional<String> createAmountText(ITypedIngredient<?> ingredient) {
		long amount = BookmarkIngredientAmountResolver.getAmount((ITypedIngredient) ingredient, ingredientManager);
		return createAmountText(ingredient, amount);
	}

	private Optional<String> createAmountText(ITypedIngredient<?> ingredient, long amount) {
		if (amount <= 0) {
			return Optional.empty();
		}
		return Optional.of(BookmarkAmountFormatter.formatTypedAmount(amount, ingredient.getType().getUid()));
	}

	private List<MergedRecipeInput> mergeRecipeInputs(List<ITypedIngredient<?>> inputs) {
		Map<IngredientMergeKey, MergedInput<?>> mergedInputs = new LinkedHashMap<>();
		for (ITypedIngredient<?> input : inputs) {
			mergeRecipeInput(mergedInputs, input);
		}
		return mergedInputs.values()
			.stream()
			.map(MergedInput::toRecipeInput)
			.toList();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private <T> void mergeRecipeInput(Map<IngredientMergeKey, MergedInput<?>> mergedInputs, ITypedIngredient<T> input) {
		IIngredientHelper<T> helper = ingredientManager.getIngredientHelper(input.getType());
		String uniqueId = helper.getUniqueId(input.getIngredient(), UidContext.Ingredient);
		IngredientMergeKey key = new IngredientMergeKey(input.getType().getUid(), uniqueId);
		MergedInput<T> merged = (MergedInput<T>) mergedInputs.get(key);
		if (merged == null) {
			mergedInputs.put(key, MergedInput.create(input, helper, ingredientManager));
		} else {
			merged.add(input, helper);
		}
	}

	private record IngredientMergeKey(String ingredientTypeUid, String ingredientUid) {
	}

	private record MergedRecipeInput(FavoriteRecipePanelState.RecipeInputKey key, ITypedIngredient<?> ingredient, long amount) {
	}

	private record ResolvedRecipeRowTarget(IElement<?> element, ResolvedRecipeIngredients ingredients) {
	}

	private record RecipeRowsKey(int columns, List<Integer> usableColumnsPerRow) {
	}

	private static final class MergedInput<T> {
		private final ITypedIngredient<T> first;
		private final IIngredientHelper<T> helper;
		private final IIngredientManager ingredientManager;
		private long amount;

		private MergedInput(
			ITypedIngredient<T> first,
			IIngredientHelper<T> helper,
			IIngredientManager ingredientManager,
			long amount
		) {
			this.first = first;
			this.helper = helper;
			this.ingredientManager = ingredientManager;
			this.amount = amount;
		}

		public static <T> MergedInput<T> create(
			ITypedIngredient<T> input,
			IIngredientHelper<T> helper,
			IIngredientManager ingredientManager
		) {
			long amount = BookmarkIngredientAmountResolver.getExplicitAmount(input, ingredientManager).orElse(-1);
			return new MergedInput<>(input, helper, ingredientManager, amount);
		}

		public void add(ITypedIngredient<T> input, IIngredientHelper<T> helper) {
			if (amount < 0) {
				return;
			}
			OptionalLong inputAmount = BookmarkIngredientAmountResolver.getExplicitAmount(input, ingredientManager);
			if (inputAmount.isEmpty()) {
				amount = -1;
				return;
			}
			amount = SaturatedMath.add(amount, inputAmount.getAsLong());
		}

		public MergedRecipeInput toRecipeInput() {
			if (amount < 0) {
				return new MergedRecipeInput(createKey(), first, BookmarkIngredientAmountResolver.getAmount(first, ingredientManager));
			}
			T mergedIngredient = helper.copyWithAmount(first.getIngredient(), amount);
			Optional<ITypedIngredient<T>> typedIngredient = ingredientManager.createTypedIngredient(first.getType(), mergedIngredient);
			ITypedIngredient<?> ingredient = typedIngredient.isPresent() ? typedIngredient.get() : first;
			return new MergedRecipeInput(createKey(), ingredient, amount);
		}

		private FavoriteRecipePanelState.RecipeInputKey createKey() {
			String uniqueId = helper.getUniqueId(first.getIngredient(), UidContext.Ingredient);
			return new FavoriteRecipePanelState.RecipeInputKey(first.getType().getUid(), uniqueId);
		}

	}

	private static class LayoutRecipeInputsResolver implements RecipeInputsResolver {
		private final IIngredientManager ingredientManager;
		private final IFocusFactory focusFactory;
		private final FocusedRecipeLayoutResolver focusedRecipeLayoutResolver;

		private LayoutRecipeInputsResolver(
			IRecipeManager recipeManager,
			IFocusFactory focusFactory,
			IIngredientManager ingredientManager
		) {
			this.ingredientManager = ingredientManager;
			this.focusFactory = focusFactory;
			this.focusedRecipeLayoutResolver = new FocusedRecipeLayoutResolver(recipeManager);
		}

		@Override
		public ResolvedRecipeIngredients resolveIngredients(
			FocusedRecipe recipe,
			ITypedIngredient<?> target,
			Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> inputs
		) {
			return createRecipeLayout(recipe, target)
				.map(layout -> {
					Map<Integer, BookmarkIngredientKey> selectedInputs = new HashMap<>(inputs.size());
					inputs.forEach((index, slotInput) -> selectedInputs.put(index, slotInput.selected()));
					FavoriteRecipeSlotResolver.ResolvedRecipeIngredients resolved = FavoriteRecipeSlotResolver.resolve(
						layout,
						target,
						ingredientManager,
						selectedInputs
					);
					return new ResolvedRecipeIngredients(resolved.target(), resolved.inputs());
				})
				.orElse(new ResolvedRecipeIngredients(Optional.empty(), List.of()));
		}

		private Optional<IRecipeLayoutDrawable<?>> createRecipeLayout(FocusedRecipe focusedRecipe, ITypedIngredient<?> target) {
			List<IFocus<?>> focuses = List.of(focusFactory.createFocus(RecipeIngredientRole.OUTPUT, target));
			return focusedRecipeLayoutResolver.resolve(focusedRecipe, focusFactory.createFocusGroup(focuses));
		}
	}
}

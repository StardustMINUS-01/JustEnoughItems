package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientAmountResolver;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.gui.overlay.bookmarks.BookmarkAmountFormatter;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.LayoutPlaceholderElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public class FavoriteRecipeGridSource implements IIngredientGridSource {
	@FunctionalInterface
	public interface RecipeInputsResolver {
		ResolvedRecipeIngredients resolveIngredients(FocusedRecipe recipe, ITypedIngredient<?> target);
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
	private List<IElement<?>> cachedGridElements;
	private final Map<Integer, List<IElement<?>>> cachedRecipeRows = new HashMap<>();

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
			new LayoutRecipeInputsResolver(recipeManager, focusFactory)
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
		if (panelState.displayMode() != FavoriteRecipePanelState.DisplayMode.RECIPE_ROWS || columns <= 1) {
			return getElements();
		}
		return cachedRecipeRows.computeIfAbsent(columns, this::createRecipeRows);
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

	private List<IElement<?>> createRecipeRows(int columns) {
		List<IElement<?>> rows = new ArrayList<>();
		for (FavoriteRecipeStore.Entry entry : store.entries()) {
			createRecipeRow(entry, columns).forEach(rows::add);
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
		return ingredientManager.getIngredientTypeForUid(entry.target().ingredientTypeUid())
			.flatMap(type -> createElement((IIngredientType) type, entry));
	}

	private <T> Optional<IElement<?>> createElement(IIngredientType<T> ingredientType, FavoriteRecipeStore.Entry entry) {
		return resolveIngredient(ingredientType, entry.target().ingredientUid())
			.map(ingredient -> createRecipeTargetElement(ingredient, entry.recipe()))
			.map(element -> (IElement<?>) element);
	}

	private <T> FavoriteRecipeElement<T> createRecipeTargetElement(ITypedIngredient<T> displayIngredient, FocusedRecipe recipe) {
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
			!panelState.isSortDragHidden(recipe, true, Optional.empty())
		);
	}

	private <T> FavoriteRecipeElement<T> createRecipeTargetElement(
		ITypedIngredient<T> displayIngredient,
		ITypedIngredient<?> targetIngredient,
		FocusedRecipe recipe
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
			!panelState.isSortDragHidden(recipe, true, Optional.empty())
		);
	}

	private <T> Optional<ITypedIngredient<T>> resolveIngredient(IIngredientType<T> ingredientType, String ingredientUid) {
		return ingredientManager.getTypedIngredientByUid(ingredientType, ingredientUid);
	}

	private List<IElement<?>> createRecipeRow(FavoriteRecipeStore.Entry entry, int columns) {
		Optional<ResolvedRecipeRowTarget> target = createRecipeRowTarget(entry);
		if (target.isEmpty()) {
			return List.of();
		}
		IElement<?> targetElement = target.get().element();
		ResolvedRecipeIngredients resolvedIngredients = target.get().ingredients();
		List<IElement<?>> row = new ArrayList<>();
		row.add(targetElement);
		if (panelState.isRecipeRowCollapsed(entry.recipe())) {
			addPlaceholders(row, columns);
			return row;
		}
		panelState.orderRecipeInputs(
				entry.recipe(),
				mergeRecipeInputs(resolvedIngredients.inputs()),
				MergedRecipeInput::key
			)
			.stream()
			.map(ingredient -> createRecipeInputElement(ingredient, entry.recipe()))
			.forEach(row::add);
		addPlaceholdersToCompleteRows(row, columns);
		return row;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Optional<ResolvedRecipeRowTarget> createRecipeRowTarget(FavoriteRecipeStore.Entry entry) {
		return ingredientManager.getIngredientTypeForUid(entry.target().ingredientTypeUid())
			.flatMap(type -> createRecipeRowTarget((IIngredientType) type, entry));
	}

	private <T> Optional<ResolvedRecipeRowTarget> createRecipeRowTarget(IIngredientType<T> ingredientType, FavoriteRecipeStore.Entry entry) {
		return resolveIngredient(ingredientType, entry.target().ingredientUid())
			.map(displayIngredient -> {
				ResolvedRecipeIngredients resolvedIngredients = recipeInputsResolver.resolveIngredients(entry.recipe(), displayIngredient);
				ITypedIngredient<?> targetIngredient = resolvedIngredients.target()
					.orElse(displayIngredient);
				FavoriteRecipeElement<T> targetElement = createRecipeTargetElement(displayIngredient, targetIngredient, entry.recipe());
				return new ResolvedRecipeRowTarget(targetElement, resolvedIngredients);
			});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private FavoriteRecipeElement<?> createRecipeInputElement(MergedRecipeInput input, FocusedRecipe recipe) {
		return createRecipeInputElement((ITypedIngredient) input.ingredient(), input.amount(), recipe, input.key());
	}

	private <T> FavoriteRecipeElement<T> createRecipeInputElement(
		ITypedIngredient<T> ingredient,
		long amount,
		FocusedRecipe recipe,
		FavoriteRecipePanelState.RecipeInputKey inputKey
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
			!panelState.isSortDragHidden(recipe, false, Optional.of(inputKey))
		);
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

	private static void addPlaceholders(List<IElement<?>> row, int columns) {
		while (row.size() < columns) {
			row.add(LayoutPlaceholderElement.INSTANCE);
		}
	}

	private static void addPlaceholdersToCompleteRows(List<IElement<?>> row, int columns) {
		while (row.size() % columns != 0) {
			row.add(LayoutPlaceholderElement.INSTANCE);
		}
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
			amount = saturatedAdd(amount, inputAmount.getAsLong());
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

		private static long saturatedAdd(long first, long second) {
			try {
				return Math.addExact(first, second);
			} catch (ArithmeticException e) {
				return Long.MAX_VALUE;
			}
		}
	}

	private static class LayoutRecipeInputsResolver implements RecipeInputsResolver {
		private final IRecipeManager recipeManager;
		private final IFocusFactory focusFactory;

		private LayoutRecipeInputsResolver(IRecipeManager recipeManager, IFocusFactory focusFactory) {
			this.recipeManager = recipeManager;
			this.focusFactory = focusFactory;
		}

		@Override
		public ResolvedRecipeIngredients resolveIngredients(FocusedRecipe recipe, ITypedIngredient<?> target) {
			return createRecipeLayout(recipe, target)
				.map(layout -> {
					FavoriteRecipeSlotResolver.ResolvedRecipeIngredients resolved = FavoriteRecipeSlotResolver.resolve(layout, target);
					return new ResolvedRecipeIngredients(resolved.target(), resolved.inputs());
				})
				.orElse(new ResolvedRecipeIngredients(Optional.empty(), List.of()));
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		private Optional<IRecipeLayoutDrawable<?>> createRecipeLayout(FocusedRecipe focusedRecipe, ITypedIngredient<?> target) {
			Optional<RecipeType<?>> recipeType = recipeManager.getRecipeType(focusedRecipe.recipeTypeUid());
			if (recipeType.isEmpty()) {
				return Optional.empty();
			}
			IRecipeCategory recipeCategory = recipeManager.getRecipeCategory((RecipeType) recipeType.get());
			List<IFocus<?>> focuses = List.of(focusFactory.createFocus(RecipeIngredientRole.OUTPUT, target));
			return recipeManager.createRecipeLookup(recipeCategory.getRecipeType())
				.limitFocus(focuses)
				.get()
				.filter(candidate -> Objects.equals(recipeCategory.getRegistryName(candidate), focusedRecipe.recipeUid()))
				.findFirst()
				.flatMap(candidate -> recipeManager.createRecipeLayoutDrawable(
					recipeCategory,
					candidate,
					focusFactory.createFocusGroup(focuses)
				))
				.map(layout -> (IRecipeLayoutDrawable<?>) layout);
		}
	}
}

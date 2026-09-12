package mezz.jei.gui.bookmarks;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.gui.IRecipeSlotCandidateView;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.recipes.InputSlotSelectionState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Maps saved quantities onto recipe geometry without importing unsaved candidates or missing inputs. */
public final class BookmarkRecipeSelection {
	private final IRecipeLayoutDrawable<?> layout;
	private final InputSlotSelectionState selections;
	private final Map<IRecipeSlotView, Binding> bindings = new LinkedHashMap<>();
	private final Map<Integer, Binding> inputs = new LinkedHashMap<>();

	public BookmarkRecipeSelection(IRecipeLayoutDrawable<?> layout, List<RecipeChainInput> saved, IIngredientManager manager) {
		this(layout, saved, manager, Map.of());
	}

	public BookmarkRecipeSelection(IRecipeLayoutDrawable<?> layout, List<RecipeChainInput> saved, IIngredientManager manager,
		Map<Integer, BookmarkIngredientKey> previousChoices) {
		this.layout = layout;
		selections = new InputSlotSelectionState(manager);
		Map<Integer, Long> remaining = new HashMap<>();
		saved.forEach(input -> remaining.put(input.index(), Math.max(1, input.metadata().factor())));
		Map<Integer, BookmarkIngredientKey> selected = new LinkedHashMap<>();
		Map<Integer, List<ITypedIngredient<?>>> candidates = new LinkedHashMap<>();
		List<IRecipeSlotView> slots = layout.getRecipeSlotsView().getSlotViews();
		List<List<BookmarkIngredientKey>> slotKeys = slots.stream().map(slot -> slot.getAllIngredients()
			.map(value -> BookmarkItemMetadataFactory.createPermutationKey(value, manager)).toList())
			.toList();
		int inputIndex = 0;
		for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
			IRecipeSlotView slot = slots.get(slotIndex);
			if (slot.getRole() != RecipeIngredientRole.INPUT && slot.getRole() != RecipeIngredientRole.OUTPUT) {
				continue;
			}
			var keys = slotKeys.get(slotIndex);
			var preferred = slot.getRole() == RecipeIngredientRole.INPUT ? previousChoices.get(inputIndex) : null;
			var matches = saved.stream().filter(input -> remaining.get(input.index()) > 0 &&
				input.metadata().type().recipeRole() == slot.getRole() && matches(input, keys))
				.toList();
			RecipeChainInput source = matches.stream().filter(input -> input.selectedKey().equals(preferred)).findFirst()
				.or(() -> matches.stream().findFirst()).orElse(null);
			Binding binding = null;
			if (source != null) {
				long unit = keys.stream().filter(source.selectedKey()::equals).findFirst().or(() -> keys.stream().findFirst())
					.map(BookmarkIngredientKey::typedIngredient).map(value -> BookmarkIngredientAmountResolver.getAmount(value, manager)).orElse(1L);
				boolean hasLaterSlot = false;
				for (int later = slotIndex + 1; later < slots.size(); later++) {
					if (slots.get(later).getRole() == slot.getRole() && matches(source, slotKeys.get(later))) {
						hasLaterSlot = true;
						break;
					}
				}
				// Geometry distributes merged inputs; the last matching slot retains any edited quantity.
				long amount = hasLaterSlot ? Math.min(remaining.get(source.index()), Math.max(1, unit)) : remaining.get(source.index());
				remaining.compute(source.index(), (index, value) -> value - amount);
				List<ITypedIngredient<?>> savedCandidates = new ArrayList<>();
				var savedKeys = new java.util.LinkedHashSet<>(source.metadata().permutations());
				savedKeys.add(source.selectedKey());
				for (var key : savedKeys) {
					ITypedIngredient<?> value = key.typedIngredient();
					if (value == null && key.equals(source.selectedKey())) {
						value = source.selectedIngredient();
					}
					if (value == null) {
						value = keys.stream().filter(key::equals).map(BookmarkIngredientKey::typedIngredient).findFirst().orElse(null);
					}
					if (value != null) {
						savedCandidates.add(withAmount(value, amount, manager));
					}
				}
				binding = new Binding(source, source.metadata().factor() == 0 ? 0 : amount, List.copyOf(savedCandidates));
				bindings.put(slot, binding);
			}
			if (slot.getRole() == RecipeIngredientRole.INPUT) {
				candidates.put(inputIndex, binding == null ? List.of() : binding.candidates());
				if (binding != null) {
					inputs.put(inputIndex, binding);
					selected.put(inputIndex, binding.source().selectedKey());
				}
				inputIndex++;
			}
			if (slot instanceof IRecipeSlotCandidateView view) {
				view.setDisplayedCandidates(binding == null ? List.of() : binding.candidates());
				if (slot.getRole() == RecipeIngredientRole.OUTPUT && binding != null) {
					view.setSelectedCandidate(binding.candidates().stream()
						.filter(value -> source.selectedKey().equals(BookmarkItemMetadataFactory.createPermutationKey(value, manager)))
						.findFirst().orElse(null));
				}
			} else if (slot instanceof IRecipeSlotDrawable drawable) {
				// Selected inputs are applied together below; missing slots need explicit empty overrides.
				if (slot.getRole() == RecipeIngredientRole.OUTPUT || binding == null) {
					drawable.clearDisplayOverrides();
					var overrides = drawable.createDisplayOverrides();
					if (binding != null) {
						binding.candidates().stream().filter(value -> source.selectedKey().equals(BookmarkItemMetadataFactory.createPermutationKey(value, manager)))
							.findFirst().ifPresent(overrides::addTypedIngredient);
					}
				}
			}
		}
		selections.setInputCandidates(candidates);
		selections.setSelectedKeys(selected);
		selections.apply(layout);
	}

	public List<ITypedIngredient<?>> getCandidates(IRecipeSlotView slot) {
		return Optional.ofNullable(bindings.get(slot)).map(Binding::candidates).orElse(List.of());
	}

	public Optional<RecipeChainInput> source(IRecipeSlotView slot) {
		return Optional.ofNullable(bindings.get(slot)).map(Binding::source);
	}

	public Map<Integer, BookmarkIngredientKey> selectedKeys() { return selections.selectedKeys(); }

	private static boolean matches(RecipeChainInput input, List<BookmarkIngredientKey> keys) {
		return input.selectedKey() != null && (keys.contains(input.selectedKey()) || input.metadata().permutations().stream().anyMatch(keys::contains));
	}

	public boolean scroll(double x, double y, double delta, boolean synchronize, BookmarkList bookmarks) {
		var before = selections.selectedKeys();
		if (!selections.scroll(layout, x, y, delta, synchronize)) {
			return false;
		}
		return applySelectedInputs(before, bookmarks);
	}

	public boolean select(IRecipeSlotView slot, ITypedIngredient<?> ingredient, boolean synchronize, BookmarkList bookmarks) {
		var before = selections.selectedKeys();
		return selections.select(layout, slot, ingredient, synchronize, false) && applySelectedInputs(before, bookmarks);
	}

	private boolean applySelectedInputs(Map<Integer, BookmarkIngredientKey> before, BookmarkList bookmarks) {
		var after = selections.selectedKeys();
		List<Choice> choices = inputs.entrySet().stream().map(entry -> {
				Binding binding = entry.getValue();
				return new Choice(binding.source().index(), binding.source().selectedKey(), after.get(entry.getKey()), binding.amount());
			})
			.toList();
		if (!bookmarks.applyRecipeInputChoices(choices)) {
			selections.setSelectedKeys(before);
			selections.apply(layout);
		}
		return true;
	}

	private static <T> ITypedIngredient<T> withAmount(ITypedIngredient<T> ingredient, long amount, IIngredientManager manager) {
		T value = manager.getIngredientHelper(ingredient.getType()).copyWithAmount(ingredient.getIngredient(), Math.max(1, amount));
		return manager.createTypedIngredient(ingredient.getType(), value, false).orElse(ingredient);
	}

	public record Choice(int sourceIndex, BookmarkIngredientKey before, BookmarkIngredientKey after, long amount) {}
	private record Binding(RecipeChainInput source, long amount, List<ITypedIngredient<?>> candidates) {}
}

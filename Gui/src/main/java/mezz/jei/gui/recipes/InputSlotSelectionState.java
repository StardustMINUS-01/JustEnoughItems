package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Ephemeral input choices for one visible recipe layout.
 */
public final class InputSlotSelectionState {
	private final IIngredientManager ingredientManager;
	private final Map<Integer, BookmarkIngredientKey> selectedKeys = new LinkedHashMap<>();

	public InputSlotSelectionState(IIngredientManager ingredientManager) {
		this.ingredientManager = ingredientManager;
	}

	public Map<Integer, BookmarkIngredientKey> selectedKeys() {
		return Map.copyOf(selectedKeys);
	}

	public Map<Integer, BookmarkIngredientKey> currentSelections(IRecipeLayoutDrawable<?> recipeLayout) {
		Map<Integer, BookmarkIngredientKey> selections = new LinkedHashMap<>();
		List<IRecipeSlotView> inputSlots = recipeLayout.getRecipeSlotsView()
			.getSlotViews(RecipeIngredientRole.INPUT);
		for (int i = 0; i < inputSlots.size(); i++) {
			IRecipeSlotView slot = inputSlots.get(i);
			BookmarkIngredientKey explicit = selectedKeys.get(i);
			if (explicit != null && findByKey(slot, explicit).isPresent()) {
				selections.put(i, explicit);
				continue;
			}
			int slotIndex = i;
			slot.getDisplayedIngredient()
				.map(this::key)
				.ifPresent(displayed -> selections.put(slotIndex, displayed));
		}
		return Map.copyOf(selections);
	}

	public void clear() {
		selectedKeys.clear();
	}

	public void clear(IRecipeLayoutDrawable<?> recipeLayout) {
		selectedKeys.clear();
		recipeLayout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT).stream()
			.filter(IRecipeSlotDrawable.class::isInstance)
			.map(IRecipeSlotDrawable.class::cast)
			.forEach(IRecipeSlotDrawable::clearDisplayOverrides);
	}

	public IRecipeSlotsView createTransferSlotsView(IRecipeLayoutDrawable<?> recipeLayout) {
		List<IRecipeSlotView> slots = recipeLayout.getRecipeSlotsView().getSlotViews();
		List<IRecipeSlotView> transferSlots = new java.util.ArrayList<>(slots.size());
		int inputSlotIndex = 0;
		for (IRecipeSlotView slot : slots) {
			if (slot.getRole() == RecipeIngredientRole.INPUT && selectedKeys.containsKey(inputSlotIndex)) {
				Optional<ITypedIngredient<?>> selected = resolve(slot, inputSlotIndex);
				if (selected.isPresent()) {
					transferSlots.add(new SelectedInputSlotView(slot, selected.get()));
					inputSlotIndex++;
					continue;
				}
			}
			transferSlots.add(slot);
			if (slot.getRole() == RecipeIngredientRole.INPUT) {
				inputSlotIndex++;
			}
		}
		return () -> List.copyOf(transferSlots);
	}

	public Optional<ITypedIngredient<?>> resolve(IRecipeSlotView slot, int inputSlotIndex) {
		BookmarkIngredientKey selectedKey = selectedKeys.get(inputSlotIndex);
		if (selectedKey != null) {
			Optional<ITypedIngredient<?>> selected = findByKey(slot, selectedKey);
			if (selected.isPresent()) {
				return selected;
			}
			selectedKeys.remove(inputSlotIndex);
		}
		return slot.getDisplayedIngredient().or(() -> slot.getAllIngredients().findFirst());
	}

	public boolean scroll(
		IRecipeLayoutDrawable<?> recipeLayout,
		double mouseX,
		double mouseY,
		double scrollDelta,
		boolean synchronizeFamily
	) {
		if (scrollDelta == 0) {
			return false;
		}
		Optional<RecipeSlotUnderMouse> hoveredSlot = recipeLayout.getSlotUnderMouse(mouseX, mouseY);
		if (hoveredSlot.isEmpty() || hoveredSlot.get().slot().getRole() != RecipeIngredientRole.INPUT) {
			return false;
		}

		List<IRecipeSlotView> inputSlots = recipeLayout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT);
		int inputSlotIndex = findInputSlotIndex(inputSlots, hoveredSlot.get().slot());
		if (inputSlotIndex < 0) {
			return false;
		}
		List<ITypedIngredient<?>> candidates = inputSlots.get(inputSlotIndex).getAllIngredients().toList();
		if (candidates.size() <= 1) {
			return false;
		}

		ITypedIngredient<?> current = resolve(inputSlots.get(inputSlotIndex), inputSlotIndex).orElse(null);
		int currentIndex = indexOf(candidates, current);
		if (currentIndex < 0) {
			return false;
		}
		int direction = (int) Math.signum(scrollDelta);
		ITypedIngredient<?> selected = candidates.get(Math.floorMod(currentIndex - direction, candidates.size()));
		BookmarkIngredientKey selectedKey = key(selected);

		if (synchronizeFamily) {
			Set<BookmarkIngredientKey> family = permutationKeys(inputSlots.get(inputSlotIndex));
			for (int index = 0; index < inputSlots.size(); index++) {
				if (family.equals(permutationKeys(inputSlots.get(index))) && findByKey(inputSlots.get(index), selectedKey).isPresent()) {
					selectedKeys.put(index, selectedKey);
				}
			}
		} else {
			selectedKeys.put(inputSlotIndex, selectedKey);
		}

		apply(recipeLayout);
		return true;
	}

	private int findInputSlotIndex(List<IRecipeSlotView> inputSlots, IRecipeSlotView hoveredSlot) {
		int directIndex = inputSlots.indexOf(hoveredSlot);
		if (directIndex >= 0) {
			return directIndex;
		}
		Set<BookmarkIngredientKey> hoveredCandidates = permutationKeys(hoveredSlot);
		for (int index = 0; index < inputSlots.size(); index++) {
			if (hoveredCandidates.equals(permutationKeys(inputSlots.get(index)))) {
				return index;
			}
		}
		return -1;
	}

	public void apply(IRecipeLayoutDrawable<?> recipeLayout) {
		List<IRecipeSlotView> inputSlots = recipeLayout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT);
		for (int index = 0; index < inputSlots.size(); index++) {
			IRecipeSlotView slot = inputSlots.get(index);
			int inputSlotIndex = index;
			if (!(slot instanceof IRecipeSlotDrawable drawable)) {
				continue;
			}
			BookmarkIngredientKey selectedKey = selectedKeys.get(inputSlotIndex);
			if (selectedKey != null) {
				drawable.clearDisplayOverrides();
				findByKey(slot, selectedKey)
					.ifPresentOrElse(
						selected -> drawable.createDisplayOverrides().addTypedIngredient(selected),
						() -> selectedKeys.remove(inputSlotIndex)
					);
			}
		}
	}

	private Optional<ITypedIngredient<?>> findByKey(IRecipeSlotView slot, BookmarkIngredientKey key) {
		return slot.getAllIngredients()
			.filter(candidate -> key.equals(key(candidate)))
			.findFirst();
	}

	private int indexOf(List<ITypedIngredient<?>> candidates, ITypedIngredient<?> ingredient) {
		if (ingredient == null) {
			return -1;
		}
		BookmarkIngredientKey key = key(ingredient);
		for (int index = 0; index < candidates.size(); index++) {
			if (key.equals(key(candidates.get(index)))) {
				return index;
			}
		}
		return -1;
	}

	private Set<BookmarkIngredientKey> permutationKeys(IRecipeSlotView slot) {
		return slot.getAllIngredients().map(this::key).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	private BookmarkIngredientKey key(ITypedIngredient<?> ingredient) {
		return BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager);
	}

	private record SelectedInputSlotView(IRecipeSlotView delegate, ITypedIngredient<?> selected) implements IRecipeSlotView {
		@Override
		public java.util.stream.Stream<ITypedIngredient<?>> getAllIngredients() {
			return java.util.stream.Stream.of(selected);
		}

		@Override
		public List<ITypedIngredient<?>> getAllIngredientsList() {
			return List.of(selected);
		}

		@Override
		public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
			return Optional.of(selected);
		}

		@Override
		public RecipeIngredientRole getRole() {
			return delegate.getRole();
		}

		@Override
		public void drawHighlight(net.minecraft.client.gui.GuiGraphics guiGraphics, int color) {
			delegate.drawHighlight(guiGraphics, color);
		}

		@Override
		public Optional<String> getSlotName() {
			return delegate.getSlotName();
		}
	}
}

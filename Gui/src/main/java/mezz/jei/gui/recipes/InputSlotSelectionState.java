/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.gui.IRecipeSlotCandidateView;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Ephemeral input choices for one visible recipe layout.
 */
public final class InputSlotSelectionState {
	private final IIngredientManager ingredientManager;
	private final @Nullable Predicate<ITypedIngredient<?>> candidateFilter;
	private final Map<Integer, BookmarkIngredientKey> selectedKeys = new LinkedHashMap<>();
	private final Map<Integer, List<ITypedIngredient<?>>> filteredCandidates = new LinkedHashMap<>();

	public InputSlotSelectionState(IIngredientManager ingredientManager) {
		this.ingredientManager = ingredientManager;
		this.candidateFilter = null;
	}

	public InputSlotSelectionState(
		IIngredientManager ingredientManager,
		Predicate<ITypedIngredient<?>> candidateFilter
	) {
		this.ingredientManager = ingredientManager;
		this.candidateFilter = candidateFilter;
	}

	public Map<Integer, BookmarkIngredientKey> selectedKeys() {
		return Map.copyOf(selectedKeys);
	}

	public Map<Integer, List<ITypedIngredient<?>>> filteredCandidates() {
		return Map.copyOf(filteredCandidates);
	}

	public void setInputCandidates(Map<Integer, List<ITypedIngredient<?>>> candidates) {
		filteredCandidates.clear();
		candidates.forEach((index, values) -> filteredCandidates.put(index, List.copyOf(values)));
	}

	public void applyCandidateFilter(IRecipeLayoutDrawable<?> recipeLayout) {
		filteredCandidates.clear();
		if (candidateFilter == null) {
			return;
		}
		List<IRecipeSlotView> inputSlots = recipeLayout.getRecipeSlotsView()
			.getSlotViews(RecipeIngredientRole.INPUT);
		Map<BookmarkIngredientKey, Boolean> matchesByCandidate = new HashMap<>();
		for (int index = 0; index < inputSlots.size(); index++) {
			List<ITypedIngredient<?>> candidates = getCandidates(inputSlots.get(index)).toList();
			List<ITypedIngredient<?>> matches = candidates.stream()
				.filter(candidate -> matchesByCandidate.computeIfAbsent(key(candidate), ignored -> candidateFilter.test(candidate)))
				.toList();
			if (!matches.isEmpty() && matches.size() < candidates.size()) {
				filteredCandidates.put(index, matches);
				if (inputSlots.get(index) instanceof IRecipeSlotCandidateView candidateView) {
					candidateView.setDisplayedCandidates(matches);
				}
			}
		}
	}

	public boolean hasSelections() {
		return !selectedKeys.isEmpty();
	}

	public void setSelectedKeys(Map<Integer, BookmarkIngredientKey> keys) {
		selectedKeys.clear();
		selectedKeys.putAll(keys);
	}

	public Map<Integer, BookmarkIngredientKey> currentSelections(IRecipeLayoutDrawable<?> recipeLayout) {
		Map<Integer, BookmarkIngredientKey> selections = new LinkedHashMap<>();
		List<IRecipeSlotView> inputSlots = recipeLayout.getRecipeSlotsView()
			.getSlotViews(RecipeIngredientRole.INPUT);
		for (int i = 0; i < inputSlots.size(); i++) {
			IRecipeSlotView slot = inputSlots.get(i);
			BookmarkIngredientKey explicit = selectedKeys.get(i);
			if (explicit != null && findByKey(slot, i, explicit).isPresent()) {
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

	public IRecipeSlotsView createTransferSlotsView(IRecipeLayoutDrawable<?> recipeLayout) {
		IRecipeSlotsView baseView = recipeLayout.getRecipeSlotsView();
		if (selectedKeys.isEmpty() && filteredCandidates.isEmpty()) {
			return baseView;
		}
		List<IRecipeSlotView> slots = baseView.getSlotViews();
		List<IRecipeSlotView> transferSlots = new ArrayList<>(slots.size());
		int inputSlotIndex = 0;
		for (IRecipeSlotView slot : slots) {
			if (slot.getRole() == RecipeIngredientRole.INPUT) {
				List<ITypedIngredient<?>> filtered = filteredCandidates.get(inputSlotIndex);
				if (selectedKeys.containsKey(inputSlotIndex)) {
					Optional<ITypedIngredient<?>> selected = resolve(slot, inputSlotIndex);
					if (selected.isPresent()) {
						transferSlots.add(new FilteredRecipeSlotView(slot, List.of(selected.get())));
						inputSlotIndex++;
						continue;
					}
				} else if (filtered != null) {
					transferSlots.add(new FilteredRecipeSlotView(slot, listCandidates(slot, inputSlotIndex)));
					inputSlotIndex++;
					continue;
				}
			}
			transferSlots.add(slot);
			if (slot.getRole() == RecipeIngredientRole.INPUT) {
				inputSlotIndex++;
			}
		}
		List<IRecipeSlotView> immutableTransferSlots = Collections.unmodifiableList(transferSlots);
		return () -> immutableTransferSlots;
	}

	public Optional<ITypedIngredient<?>> resolve(IRecipeSlotView slot, int inputSlotIndex) {
		BookmarkIngredientKey selectedKey = selectedKeys.get(inputSlotIndex);
		if (selectedKey != null) {
			Optional<ITypedIngredient<?>> selected = findByKey(slot, inputSlotIndex, selectedKey);
			if (selected.isPresent()) {
				return selected;
			}
			selectedKeys.remove(inputSlotIndex);
		}
		List<ITypedIngredient<?>> filtered = filteredCandidates.get(inputSlotIndex);
		if (filtered == null) {
			return slot.getDisplayedIngredient();
		}
		filtered = listCandidates(slot, inputSlotIndex);
		Optional<ITypedIngredient<?>> displayed = slot.getDisplayedIngredient();
		if (displayed.isPresent() && indexOf(filtered, displayed.get()) >= 0) {
			return displayed;
		}
		return filtered.stream().findFirst();
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
		List<ITypedIngredient<?>> candidates = listCandidates(inputSlots.get(inputSlotIndex), inputSlotIndex);
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
		return select(recipeLayout, inputSlots.get(inputSlotIndex), selected, synchronizeFamily, false);
	}

	public boolean select(
		IRecipeLayoutDrawable<?> recipeLayout,
		IRecipeSlotView sourceSlot,
		ITypedIngredient<?> selected,
		boolean synchronizeFamily,
		boolean toggle
	) {
		if (sourceSlot.getRole() != RecipeIngredientRole.INPUT) {
			return false;
		}
		List<IRecipeSlotView> inputSlots = recipeLayout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT);
		int inputSlotIndex = findInputSlotIndex(inputSlots, sourceSlot);
		BookmarkIngredientKey selectedKey = key(selected);
		if (inputSlotIndex < 0 || findByKey(inputSlots.get(inputSlotIndex), inputSlotIndex, selectedKey).isEmpty()) {
			return false;
		}
		boolean clear = toggle && selectedKey.equals(selectedKeys.get(inputSlotIndex));
		Set<BookmarkIngredientKey> family = synchronizeFamily ? permutationKeys(inputSlots.get(inputSlotIndex), inputSlotIndex) : Set.of();
		for (int index = 0; index < inputSlots.size(); index++) {
			if (index != inputSlotIndex && (!synchronizeFamily || !family.equals(permutationKeys(inputSlots.get(index), index)))) {
				continue;
			}
			if (clear) {
				selectedKeys.remove(index);
				setSelectedCandidate(inputSlots.get(index), null);
			} else {
				selectedKeys.put(index, selectedKey);
			}
		}
		apply(recipeLayout);
		return true;
	}

	private int findInputSlotIndex(List<IRecipeSlotView> inputSlots, IRecipeSlotView hoveredSlot) {
		int directIndex = inputSlots.indexOf(hoveredSlot);
		if (directIndex >= 0) {
			return directIndex;
		}
		Set<BookmarkIngredientKey> hoveredCandidates = unfilteredPermutationKeys(hoveredSlot);
		for (int index = 0; index < inputSlots.size(); index++) {
			if (hoveredCandidates.equals(unfilteredPermutationKeys(inputSlots.get(index)))) {
				return index;
			}
		}
		return -1;
	}

	public void apply(IRecipeLayoutDrawable<?> recipeLayout) {
		if (selectedKeys.isEmpty()) {
			return;
		}
		List<IRecipeSlotView> inputSlots = recipeLayout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT);
		for (int index = 0; index < inputSlots.size(); index++) {
			IRecipeSlotView slot = inputSlots.get(index);
			int inputSlotIndex = index;
			BookmarkIngredientKey selectedKey = selectedKeys.get(inputSlotIndex);
			if (selectedKey != null) {
				var selected = findByKey(slot, inputSlotIndex, selectedKey);
				setSelectedCandidate(slot, selected.orElse(null));
				if (selected.isEmpty()) {
					selectedKeys.remove(inputSlotIndex);
				}
			}
		}
	}

	private static void setSelectedCandidate(IRecipeSlotView slot, @Nullable ITypedIngredient<?> selected) {
		if (slot instanceof IRecipeSlotCandidateView view) {
			view.setSelectedCandidate(selected);
		} else if (slot instanceof IRecipeSlotDrawable drawable) {
			drawable.clearDisplayOverrides();
			if (selected != null) {
				drawable.createDisplayOverrides().addTypedIngredient(selected);
			}
		}
	}

	private static Stream<ITypedIngredient<?>> getCandidates(IRecipeSlotView slot) {
		return slot instanceof IRecipeSlotCandidateView view ? view.getCandidateIngredients() : slot.getDisplayedIngredients();
	}

	private Optional<ITypedIngredient<?>> findByKey(IRecipeSlotView slot, int inputSlotIndex, BookmarkIngredientKey key) {
		return streamCandidates(slot, inputSlotIndex)
			.filter(candidate -> key.equals(key(candidate)))
			.findFirst();
	}

	private Stream<ITypedIngredient<?>> streamCandidates(IRecipeSlotView slot, int inputSlotIndex) {
		if (slot instanceof IRecipeSlotCandidateView view) {
			return view.getCandidateIngredients();
		}
		List<ITypedIngredient<?>> filtered = filteredCandidates.get(inputSlotIndex);
		return filtered == null ? getCandidates(slot) : filtered.stream();
	}

	private List<ITypedIngredient<?>> listCandidates(IRecipeSlotView slot, int inputSlotIndex) {
		if (slot instanceof IRecipeSlotCandidateView view) {
			return view.getCandidateIngredients().toList();
		}
		List<ITypedIngredient<?>> filtered = filteredCandidates.get(inputSlotIndex);
		return filtered == null ? getCandidates(slot).toList() : filtered;
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

	private Set<BookmarkIngredientKey> permutationKeys(IRecipeSlotView slot, int inputSlotIndex) {
		return streamCandidates(slot, inputSlotIndex)
			.map(this::key)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	private Set<BookmarkIngredientKey> unfilteredPermutationKeys(IRecipeSlotView slot) {
		return slot.getAllIngredients()
			.map(this::key)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	private BookmarkIngredientKey key(ITypedIngredient<?> ingredient) {
		return BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager);
	}

}

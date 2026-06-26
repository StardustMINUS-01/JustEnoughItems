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
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Mirrors GTNH NEI GuiFavoriteButton's favoriteResult/selectedResult target selection.
 */
public final class FavoriteRecipeTargetSelector {
	private final List<Target> targets;
	private final Function<ITypedIngredient<?>, BookmarkIngredientKey> keyFactory;
	private int selectedIndex;

	private FavoriteRecipeTargetSelector(
		List<Target> targets,
		Function<ITypedIngredient<?>, BookmarkIngredientKey> keyFactory,
		int selectedIndex
	) {
		this.targets = targets;
		this.keyFactory = keyFactory;
		this.selectedIndex = selectedIndex;
	}

	public static FavoriteRecipeTargetSelector create(
		IRecipeLayoutDrawable<?> recipeLayout,
		Function<ITypedIngredient<?>, BookmarkIngredientKey> keyFactory,
		@Nullable BookmarkIngredientKey storedTarget
	) {
		List<Target> targets = getTargets(recipeLayout, keyFactory);
		int selectedIndex = getInitialIndex(targets, storedTarget);
		return new FavoriteRecipeTargetSelector(targets, keyFactory, selectedIndex);
	}

	public int targetCount() {
		return targets.size();
	}

	public boolean hasMultipleTargets() {
		return targets.size() > 1;
	}

	public Optional<BookmarkIngredientKey> selectedKey() {
		return selectedTarget().map(Target::key);
	}

	public Optional<IRecipeSlotDrawable> selectedSlot() {
		return selectedTarget().map(Target::slot);
	}

	public Optional<BookmarkIngredientKey> keyForSlot(IRecipeSlotDrawable slot) {
		return slot.getDisplayedIngredient()
			.or(() -> slot.getAllIngredients().findFirst())
			.map(keyFactory);
	}

	public boolean isSelectedTarget(@Nullable BookmarkIngredientKey key) {
		return key != null && selectedKey()
			.map(key::equals)
			.orElse(false);
	}

	public void selectStoredOrDefault(@Nullable BookmarkIngredientKey storedTarget) {
		this.selectedIndex = getInitialIndex(targets, storedTarget);
	}

	public boolean scroll(double scrollDelta) {
		if (targets.size() <= 1 || scrollDelta == 0) {
			return false;
		}
		int scroll = (int) Math.signum(scrollDelta);
		selectedIndex = Math.floorMod(targets.size() - scroll + selectedIndex, targets.size());
		return true;
	}

	private Optional<Target> selectedTarget() {
		if (selectedIndex < 0 || selectedIndex >= targets.size()) {
			return Optional.empty();
		}
		return Optional.of(targets.get(selectedIndex));
	}

	private static List<Target> getTargets(
		IRecipeLayoutDrawable<?> recipeLayout,
		Function<ITypedIngredient<?>, BookmarkIngredientKey> keyFactory
	) {
		Map<BookmarkIngredientKey, Target> uniqueTargets = new LinkedHashMap<>();
		for (IRecipeSlotView slotView : recipeLayout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT)) {
			if (!(slotView instanceof IRecipeSlotDrawable slot)) {
				continue;
			}
			slot.getAllIngredients()
				.findFirst()
				.map(ingredient -> new Target(keyFactory.apply(ingredient), slot))
				.ifPresent(target -> uniqueTargets.putIfAbsent(target.key(), target));
		}
		return List.copyOf(uniqueTargets.values());
	}

	private static int getInitialIndex(List<Target> targets, @Nullable BookmarkIngredientKey storedTarget) {
		if (targets.isEmpty()) {
			return -1;
		}
		if (storedTarget != null) {
			for (int i = 0; i < targets.size(); i++) {
				if (storedTarget.equals(targets.get(i).key())) {
					return i;
				}
			}
		}
		return 0;
	}

	private record Target(BookmarkIngredientKey key, IRecipeSlotDrawable slot) {
	}
}

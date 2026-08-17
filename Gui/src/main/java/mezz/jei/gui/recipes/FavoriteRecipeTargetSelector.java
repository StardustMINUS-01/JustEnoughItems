package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.config.DebugConfig;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class FavoriteRecipeTargetSelector {
	private static final Logger LOGGER = LogManager.getLogger();
	public record Target(BookmarkIngredientKey key, IRecipeSlotDrawable slot) {
	}

	private final List<Target> targets;
	private final Function<ITypedIngredient<?>, BookmarkIngredientKey> keyFactory;
	private int selectedIndex;

	private FavoriteRecipeTargetSelector(
		List<Target> targets,
		Function<ITypedIngredient<?>, BookmarkIngredientKey> keyFactory,
		@Nullable BookmarkIngredientKey storedTarget
	) {
		this.targets = targets;
		this.keyFactory = keyFactory;
		this.selectedIndex = getInitialIndex(targets, storedTarget);
	}

	public static FavoriteRecipeTargetSelector create(
		IRecipeLayoutDrawable<?> recipeLayout,
		Function<ITypedIngredient<?>, BookmarkIngredientKey> keyFactory,
		@Nullable BookmarkIngredientKey storedTarget
	) {
		List<Target> targets = getTargets(recipeLayout, keyFactory);
		return new FavoriteRecipeTargetSelector(targets, keyFactory, storedTarget);
	}

	public int targetCount() {
		return this.targets.size();
	}

	public boolean hasMultipleTargets() {
		return this.targets.size() > 1;
	}

	public Optional<BookmarkIngredientKey> selectedKey() {
		return selectedTarget()
			.map(Target::key);
	}

	public Optional<IRecipeSlotDrawable> selectedSlot() {
		return selectedTarget()
			.map(Target::slot);
	}

	public Optional<BookmarkIngredientKey> keyForSlot(IRecipeSlotDrawable slot) {
		Optional<ITypedIngredient<?>> first = slot.getAllIngredients()
			.findFirst();
		if (first.isEmpty() && DebugConfig.isDebugModeEnabled()) {
			LOGGER.info("[Bug5] keyForSlot EMPTY slot={}", slot);
		}
		return first.map(keyFactory);
	}

	public boolean isSelectedTarget(@Nullable BookmarkIngredientKey key) {
		if (key == null) {
			return false;
		}
		return selectedKey()
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
		if (this.selectedIndex < 0 || this.selectedIndex >= this.targets.size()) {
			return Optional.empty();
		}
		return Optional.of(this.targets.get(this.selectedIndex));
	}

	private static List<Target> getTargets(
		IRecipeLayoutDrawable<?> recipeLayout,
		Function<ITypedIngredient<?>, BookmarkIngredientKey> keyFactory
	) {
		Map<BookmarkIngredientKey, Target> uniqueTargets = new LinkedHashMap<>();
		recipeLayout.getRecipeSlotsView()
			.getSlotViews(RecipeIngredientRole.OUTPUT)
			.stream()
			.filter(slotView -> slotView instanceof IRecipeSlotDrawable)
			.map(slotView -> (IRecipeSlotDrawable) slotView)
			.forEach(slot -> {
				slot.getAllIngredients()
					.findFirst()
					.map(ingredient -> new Target(keyFactory.apply(ingredient), slot))
					.ifPresent(target -> uniqueTargets.putIfAbsent(target.key(), target));
			});
		return List.copyOf(uniqueTargets.values());
	}

	private static int getInitialIndex(List<Target> targets, @Nullable BookmarkIngredientKey storedTarget) {
		if (targets.isEmpty()) {
			return -1;
		}
		if (storedTarget != null) {
			for (int i = 0; i < targets.size(); i++) {
				Target target = targets.get(i);
				if (storedTarget.equals(target.key())) {
					return i;
				}
			}
		}
		return 0;
	}
}

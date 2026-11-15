package mezz.jei.library.gui.ingredients;

import mezz.jei.api.gui.builder.IIngredientConsumer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.common.Internal;
import mezz.jei.library.ingredients.DisplayIngredientAcceptor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.function.Predicate;

/**
 * Owns focus, visibility, cycling, and display overrides for a recipe slot.
 */
public final class RecipeSlotIngredients {
	private static final int MAX_DISPLAYED_INGREDIENTS = 100;

	/**
	 * All ingredients, ignoring focus and visibility.
	 * Null ingredients represent a blank drawn ingredient in the rotation.
	 */
	@Unmodifiable
	private final List<@Nullable ITypedIngredient<?>> allIngredients;
	private final @Nullable List<@Nullable ITypedIngredient<?>> focusedIngredients;

	/**
	 * Displayed ingredients, taking focus and visibility into account.
	 * Null ingredients represent a blank drawn ingredient in the rotation.
	 */
	@Unmodifiable
	private @Nullable List<@Nullable ITypedIngredient<?>> displayIngredients;

	private @Nullable List<ITypedIngredient<?>> filteredCandidates;
	private @Nullable List<ITypedIngredient<?>> selectedCandidate;
	private @Nullable Boolean selectedCandidateInScope;
	private @Nullable DisplayIngredientAcceptor displayOverrides;
	private final Runnable displayOverridesChangedListener;

	public RecipeSlotIngredients(
		List<@Nullable ITypedIngredient<?>> allIngredients,
		@Nullable List<@Nullable ITypedIngredient<?>> focusedIngredients,
		Runnable displayOverridesChangedListener
	) {
		this.allIngredients = Collections.unmodifiableList(allIngredients);
		this.focusedIngredients = focusedIngredients;
		this.displayOverridesChangedListener = displayOverridesChangedListener;
	}

	public Stream<ITypedIngredient<?>> getAllIngredients() {
		return this.allIngredients.stream()
			.filter(Objects::nonNull);
	}

	@Unmodifiable
	public List<@Nullable ITypedIngredient<?>> getAllIngredientsList() {
		return this.allIngredients;
	}

	public boolean isEmpty() {
		return this.allIngredients.isEmpty() || this.allIngredients.stream().allMatch(Objects::isNull);
	}

	public Optional<ITypedIngredient<?>> getDisplayedIngredient(ICycler cycler) {
		return cycler.getCycled(getDisplayIngredients());
	}

	public Optional<ITypedIngredient<?>> getFirstDisplayedIngredient() {
		List<@Nullable ITypedIngredient<?>> displayIngredients = getDisplayIngredients();
		if (displayIngredients.isEmpty()) {
			return Optional.empty();
		}
		return Optional.ofNullable(displayIngredients.getFirst());
	}

	public Stream<ITypedIngredient<?>> getDisplayedIngredients() {
		List<@Nullable ITypedIngredient<?>> candidates = allIngredients;
		if (displayOverrides != null) {
			candidates = displayOverrides.getAllIngredients();
		}
		IIngredientVisibility visibility = Internal.getJeiRuntime().getJeiHelpers().getIngredientVisibility();
		return candidates.stream()
			.filter(Objects::nonNull)
			.filter(ingredient -> visibility.isIngredientVisible(ingredient, UidContext.Recipe));
	}

	private List<@Nullable ITypedIngredient<?>> getDisplayIngredients() {
		if (this.selectedCandidate != null && (this.displayOverrides == null ||
			this.displayOverrides.getAllIngredients().contains(this.selectedCandidate.getFirst()))) {
			return this.selectedCandidate;
		}
		if (this.displayIngredients == null) {
			if (this.displayOverrides == null && this.filteredCandidates == null && this.focusedIngredients == null) {
				this.displayIngredients = calculateDisplayIngredients(this.allIngredients);
			} else {
				List<@Nullable ITypedIngredient<?>> candidates = getCandidateList();
				this.displayIngredients = candidates.size() > MAX_DISPLAYED_INGREDIENTS ? candidates.subList(0, MAX_DISPLAYED_INGREDIENTS) : candidates;
			}
		}
		return this.displayIngredients;
	}

	private List<@Nullable ITypedIngredient<?>> getCandidateList() {
		if (this.displayOverrides != null) {
			return this.displayOverrides.getAllIngredients();
		}
		if (this.filteredCandidates != null) {
			return this.filteredCandidates;
		}
		return this.focusedIngredients == null ? this.allIngredients : this.focusedIngredients;
	}

	public <T> List<T> getVisibleIngredients(IIngredientType<T> ingredientType) {
		IIngredientVisibility ingredientVisibility = Internal.getJeiRuntime().getJeiHelpers().getIngredientVisibility();
		return getAllIngredients()
			.filter(ingredient -> ingredientVisibility.isIngredientVisible(ingredient, UidContext.Recipe))
			.map(ingredient -> ingredient.getIngredient(ingredientType))
			.flatMap(Optional::stream)
			.toList();
	}

	public void setDisplayedCandidates(@Nullable List<ITypedIngredient<?>> candidates) {
		this.filteredCandidates = candidates;
		onDisplayOverridesChanged();
	}

	public Stream<ITypedIngredient<?>> getCandidateIngredients() {
		// Explicit focus and filtering constrain candidates; the cycling fallback never does.
		if (this.displayOverrides != null || (this.filteredCandidates == null && this.focusedIngredients == null)) {
			return getDisplayedIngredients();
		}
		return getCandidateList().stream().filter(Objects::nonNull);
	}

	public void setSelectedCandidate(@Nullable ITypedIngredient<?> candidate) {
		if (candidate == (this.selectedCandidate == null ? null : this.selectedCandidate.getFirst())) {
			return;
		}
		this.selectedCandidate = candidate == null ? null : List.of(candidate);
		this.selectedCandidateInScope = null;
		displayOverridesChangedListener.run();
	}

	public Optional<ITypedIngredient<?>> getSelectedCandidate() {
		if (selectedCandidate == null) {
			return Optional.empty();
		}
		ITypedIngredient<?> selected = selectedCandidate.getFirst();
		// Candidate lists change through the setters; visibility may change independently.
		if (selectedCandidateInScope == null) {
			selectedCandidateInScope = getCandidateList().contains(selected);
		}
		if (!selectedCandidateInScope) {
			return Optional.empty();
		}
		if (displayOverrides != null || (filteredCandidates == null && focusedIngredients == null)) {
			IIngredientVisibility visibility = Internal.getJeiRuntime().getJeiHelpers().getIngredientVisibility();
			if (!visibility.isIngredientVisible(selected, UidContext.Recipe)) {
				return Optional.empty();
			}
		}
		return Optional.of(selected);
	}

	public void clearDisplayOverrides() {
		boolean changed = this.displayOverrides != null;
		this.displayOverrides = null;
		if (changed) {
			onDisplayOverridesChanged();
		}
	}

	public boolean hasDisplayOverrides() {
		return this.displayOverrides != null;
	}

	public IIngredientConsumer createDisplayOverrides() {
		if (this.displayOverrides == null) {
			IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
			this.displayOverrides = new DisplayIngredientAcceptor(ingredientManager, this::onDisplayOverridesChanged);
			onDisplayOverridesChanged();
		}
		return this.displayOverrides;
	}

	private void onDisplayOverridesChanged() {
		this.displayIngredients = null;
		this.selectedCandidateInScope = null;
		displayOverridesChangedListener.run();
	}

	private static List<@Nullable ITypedIngredient<?>> calculateDisplayIngredients(List<@Nullable ITypedIngredient<?>> allIngredients) {
		IIngredientVisibility visibility = Internal.getJeiRuntime().getJeiHelpers().getIngredientVisibility();
		return filterVisibleIngredients(allIngredients, ingredient -> visibility.isIngredientVisible(ingredient, UidContext.Recipe), MAX_DISPLAYED_INGREDIENTS);
	}

	public static List<@Nullable ITypedIngredient<?>> filterVisibleIngredients(
		List<@Nullable ITypedIngredient<?>> ingredients,
		Predicate<ITypedIngredient<?>> isVisible,
		int limit
	) {
		List<@Nullable ITypedIngredient<?>> visible = ingredients.stream()
			.filter(ingredient -> ingredient == null || isVisible.test(ingredient))
			.limit(limit)
			.toList();
		if (!visible.isEmpty()) {
			return visible;
		}
		// If every ingredient is invisible, show them anyway so the recipe slot is not blank.
		return ingredients.stream().limit(limit).toList();
	}
}

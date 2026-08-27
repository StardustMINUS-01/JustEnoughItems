package mezz.jei.gui.overlay.elements;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.BookmarkCandidateTooltipHelper;
import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkPermutationTooltipState;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.chain.RecipeChainItem;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.ingredients.IngredientGridTooltipHelper;
import mezz.jei.gui.overlay.bookmarks.BookmarkAmountFormatter;
import mezz.jei.gui.util.FocusUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class ProjectedBookmarkElement<T> implements IElement<T> {
	private final IElement<T> delegate;
	private final BookmarkDisplayEntry<?> displayEntry;
	private final BookmarkPermutationTooltipState permutationTooltipState;

	public ProjectedBookmarkElement(IElement<T> delegate, BookmarkDisplayEntry<?> displayEntry) {
		this(delegate, displayEntry, new BookmarkPermutationTooltipState());
	}

	public ProjectedBookmarkElement(
		IElement<T> delegate,
		BookmarkDisplayEntry<?> displayEntry,
		BookmarkPermutationTooltipState permutationTooltipState
	) {
		this.delegate = delegate;
		this.displayEntry = displayEntry;
		this.permutationTooltipState = permutationTooltipState;
	}

	@Override
	public ITypedIngredient<T> getTypedIngredient() {
		return delegate.getTypedIngredient();
	}

	@Override
	public Optional<IBookmark> getBookmark() {
		return delegate.getBookmark();
	}

	@Override
	public @Nullable IDrawable createRenderOverlay() {
		return delegate.createRenderOverlay();
	}

	@Override
	public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) {
		List<IFocus<?>> focuses = focusUtil.createFocuses(getTypedIngredient(), roles);
		recipesGui.show(focuses);
	}

	public boolean handleShowRecipeClick(
		UserInput input,
		IInternalKeyMappings keyBindings,
		IRecipesGui recipesGui,
		FocusUtil focusUtil
	) {
		if (!displayEntry.metadata().type().isGraphOutput() || !input.is(keyBindings.getLeftClick())) {
			return false;
		}
		if (!input.isSimulate()) {
			delegate.show(recipesGui, focusUtil, List.of(RecipeIngredientRole.OUTPUT));
		}
		return true;
	}

	@Override
	public void getTooltip(JeiTooltip tooltip, IngredientGridTooltipHelper tooltipHelper, IIngredientRenderer<T> ingredientRenderer, IIngredientHelper<T> ingredientHelper) {
		ITypedIngredient<T> typedIngredient = createTooltipIngredient(getTypedIngredient(), ingredientHelper, displayEntry);
		boolean showToggleInputCatalyst = displayEntry.metadata().type().isGraphInput() ||
			displayEntry.metadata().type().isCatalyst();
		tooltipHelper.getIngredientTooltip(
			tooltip,
			typedIngredient,
			ingredientRenderer,
			ingredientHelper,
			true,
			showToggleInputCatalyst
		);
		addPermutationTooltip(tooltip);
		if (delegate instanceof RecipeBookmarkElement<?, ?> recipeBookmarkElement) {
			recipeBookmarkElement.addRecipeTooltipFeatures(tooltip);
		}
	}

	private void addPermutationTooltip(JeiTooltip tooltip) {
		List<BookmarkIngredientKey> permutationKeys = List.copyOf(displayEntry.metadata().permutations());
		BookmarkCandidateTooltipHelper.addTo(
			tooltip,
			permutationTooltipState,
			displayEntry.sourceIndex(),
			getTypedIngredient(),
			permutationKeys
		);
	}

	@Override
	public boolean isVisible() {
		return delegate.isVisible();
	}

	@Override
	public boolean handleClick(UserInput input, IInternalKeyMappings keyBindings) {
		return delegate.handleClick(input, keyBindings);
	}

	@Override
	public Optional<Long> getCheatGiveAmount() {
		Optional<Long> chainAmount = displayEntry.recipeChainItem()
			.map(RecipeChainItem::calculatedAmount)
			.filter(amount -> amount > 0);
		if (chainAmount.isPresent()) {
			return chainAmount;
		}
		if (displayEntry.metadata().isDefault()) {
			return Optional.empty();
		}
		long amount = displayEntry.metadata().amount();
		return amount > 0 ? Optional.of(amount) : Optional.empty();
	}

	@Override
	public void tick() {
		delegate.tick();
	}

	static <T> ITypedIngredient<T> createTooltipIngredient(
		ITypedIngredient<T> typedIngredient,
		IIngredientHelper<T> ingredientHelper,
		BookmarkDisplayEntry<?> entry
	) {
		if (!BookmarkAmountFormatter.usesFluidAmountUnits(typedIngredient.getType().getUid())) {
			return typedIngredient;
		}
		long amount = entry.recipeChainItem()
			.map(RecipeChainItem::calculatedAmount)
			.orElseGet(entry.metadata()::amount);
		if (amount <= 0) {
			return typedIngredient;
		}
		T ingredient = ingredientHelper.copyWithAmount(typedIngredient.getIngredient(), amount);
		return new TooltipIngredient<>(typedIngredient.getType(), ingredient);
	}

	private record TooltipIngredient<T>(IIngredientType<T> type, T ingredient) implements ITypedIngredient<T> {
		@Override
		public IIngredientType<T> getType() {
			return type;
		}

		@Override
		public T getIngredient() {
			return ingredient;
		}
	}
}

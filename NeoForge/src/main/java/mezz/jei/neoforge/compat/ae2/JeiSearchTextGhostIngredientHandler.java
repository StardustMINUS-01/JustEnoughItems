package mezz.jei.neoforge.compat.ae2;

import appeng.client.gui.widgets.AETextField;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler.Target;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.Internal;
import mezz.jei.gui.input.handlers.IngredientClipboardText;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.util.List;
import java.util.Optional;

public class JeiSearchTextGhostIngredientHandler<T extends Screen> implements IGhostIngredientHandler<T> {
	@Override
	public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
		return createTarget(gui, ingredient)
			.map(target -> List.<Target<I>>of(target))
			.orElseGet(List::of);
	}

	public static <I> boolean search(Screen gui, ITypedIngredient<I> ingredient, boolean simulate) {
		Optional<SearchTextTarget<I>> target = createTarget(gui, ingredient);
		if (target.isEmpty()) {
			return false;
		}
		if (!simulate) {
			target.get().accept(ingredient.getIngredient());
		}
		return true;
	}

	private static <I> Optional<SearchTextTarget<I>> createTarget(Screen gui, ITypedIngredient<I> ingredient) {
		Optional<String> searchText = toSearchText(ingredient);
		if (searchText.isEmpty()) {
			return Optional.empty();
		}
		Optional<AETextField> searchField = findSearchField(gui);
		if (searchField.isEmpty() || !searchField.get().isTooltipAreaVisible()) {
			return Optional.empty();
		}
		Rect2i area = searchField.get().getTooltipArea();
		return Optional.of(new SearchTextTarget<>(area, searchField.get(), gui, searchText.get()));
	}

	@Override
	public void onComplete() {
	}

	private static Optional<AETextField> findSearchField(Screen gui) {
		return gui.children().stream()
			.filter(AETextField.class::isInstance)
			.map(AETextField.class::cast)
			.findFirst();
	}

	private static <I> Optional<String> toSearchText(ITypedIngredient<I> ingredient) {
		String searchText = IngredientClipboardText.getIngredientName(
			ingredient,
			Internal.getJeiRuntime().getIngredientManager()
		).trim();
		return searchText.isEmpty() ? Optional.empty() : Optional.of(searchText);
	}

	private record SearchTextTarget<I>(
		Rect2i area,
		AETextField searchField,
		Screen gui,
		String searchText
	) implements Target<I> {
		@Override
		public Rect2i getArea() {
			return area;
		}

		@Override
		public void accept(I ingredient) {
			searchField.setValue(searchText);
			searchField.setCursorPosition(0);
			searchField.setHighlightPos(0);
			gui.setFocused(searchField);
		}
	}
}

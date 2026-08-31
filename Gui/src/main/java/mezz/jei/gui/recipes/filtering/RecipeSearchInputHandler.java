package mezz.jei.gui.recipes.filtering;

import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.GuiTextFieldFilter;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.TextFieldInputHandler;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;

public final class RecipeSearchInputHandler extends TextFieldInputHandler {
	private final GuiTextFieldFilter searchField;
	private final RecipeFilterSettings settings;
	private final Runnable applyFilter;

	public RecipeSearchInputHandler(
		GuiTextFieldFilter searchField,
		RecipeFilterSettings settings,
		Runnable applyFilter
	) {
		super(searchField);
		this.searchField = searchField;
		this.settings = settings;
		this.applyFilter = applyFilter;
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(
		Screen screen,
		UserInput input,
		IInternalKeyMappings keyBindings
	) {
		if (searchField.isFocused() && input.is(keyBindings.getEnterKey())) {
			if (!input.isSimulate()) {
				commitAndUnfocus();
			}
			return Optional.of(this);
		}
		if (searchField.isFocused() && input.is(keyBindings.getEscapeKey())) {
			if (!input.isSimulate()) {
				settings.cancelQuery();
				searchField.setValue(settings.getDraftQuery());
				searchField.setFocused(false);
			}
			return Optional.of(this);
		}
		if (input.is(keyBindings.getFocusSearch())) {
			if (!input.isSimulate()) {
				searchField.setFocused(true);
			}
			return Optional.of(this);
		}
		if (searchField.isMouseOver(input.getMouseX(), input.getMouseY()) &&
			input.is(keyBindings.getHoveredClearSearchBar())
		) {
			if (!input.isSimulate()) {
				searchField.setValue("");
				if (settings.clearQuery()) {
					applyFilter.run();
				}
				searchField.setFocused(true);
			}
			return Optional.of(this);
		}
		return super.handleUserInput(screen, input, keyBindings);
	}

	public void commitAndUnfocus() {
		if (settings.commitQuery()) {
			applyFilter.run();
		}
		searchField.setFocused(false);
	}
}

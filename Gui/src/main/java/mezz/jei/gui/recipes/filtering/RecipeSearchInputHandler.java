package mezz.jei.gui.recipes.filtering;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.interaction.InputAction;
import mezz.jei.gui.input.GuiTextFieldFilter;
import mezz.jei.gui.input.handlers.SearchInputLayer;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class RecipeSearchInputHandler extends SearchInputLayer {
	private final GuiTextFieldFilter searchField;
	private final RecipeFilterSettings settings;
	private final Runnable applyFilter;
	private final BooleanSupplier active;

	public RecipeSearchInputHandler(
		GuiTextFieldFilter searchField,
		RecipeFilterSettings settings,
		Runnable applyFilter,
		BooleanSupplier active
	) {
		super(searchField, active, searchField::clearFocus);
		this.searchField = searchField;
		this.settings = settings;
		this.applyFilter = applyFilter;
		this.active = active;
	}

	@Override
	public Optional<IInputInteraction> beginInput(
		Screen screen,
		UserInput input,
		IInternalKeyMappings keyBindings
	) {
		if (!active.getAsBoolean()) {
			return Optional.empty();
		}
		if (searchField.isFocused() && input.is(keyBindings.getEnterKey())) {
			return Optional.of(InputAction.run(this::commitAndUnfocus));
		}
		if (searchField.isFocused() && input.is(keyBindings.getEscapeKey())) {
			return Optional.of(InputAction.run(() -> {
				settings.cancelQuery();
				searchField.setValue(settings.getDraftQuery());
				searchField.clearFocus();
			}));
		}
		if (input.is(keyBindings.getFocusSearch())) {
			return Optional.of(InputAction.run(searchField::focus));
		}
		if (searchField.isMouseOver(input.getMouseX(), input.getMouseY()) &&
			input.is(keyBindings.getHoveredClearSearchBar())
		) {
			return Optional.of(InputAction.run(() -> {
					searchField.setValue("");
					if (settings.clearQuery()) {
						applyFilter.run();
					}
					searchField.focus();
				})
				.within(searchField::isMouseOver));
		}
		if (!input.is(keyBindings.getPreviousSearch()) && !input.is(keyBindings.getNextSearch()) &&
			(input.is(keyBindings.getTabKey()) || input.ifKeyboardEvent((key, scanCode, modifiers) -> key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN))
		) {
			return Optional.empty();
		}
		return super.beginInput(screen, input, keyBindings);
	}

	public void commitAndUnfocus() {
		if (settings.commitQuery()) {
			applyFilter.run();
		}
		searchField.clearFocus();
	}

	@Override
	public void clearFocus() {
		if (searchField.isFocused()) {
			commitAndUnfocus();
		}
	}

	@Override
	public void resetInput() {
		searchField.clearFocus();
	}
}

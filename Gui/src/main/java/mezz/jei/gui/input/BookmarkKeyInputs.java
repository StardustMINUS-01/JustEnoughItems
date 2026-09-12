package mezz.jei.gui.input;

import mezz.jei.common.input.keys.IJeiKeyMappingWithExtraModifiers;

import mezz.jei.common.input.IInternalKeyMappings;

public final class BookmarkKeyInputs {
	private BookmarkKeyInputs() {
	}

	public static boolean isPlainBookmarkKey(UserInput input, IInternalKeyMappings keyBindings) {
		return !InputModifiers.hasAnyModifier(input.getModifiers()) &&
			input.is(keyBindings.getBookmark());
	}

	public static boolean isShiftBookmarkKey(UserInput input, IInternalKeyMappings keyBindings) {
		int modifiers = input.getModifiers();
		return InputModifiers.hasShift(modifiers) &&
			!InputModifiers.hasControlOrAlt(modifiers) &&
			IJeiKeyMappingWithExtraModifiers.matchesShortcut(keyBindings.getBookmark(), input.getKey());
	}

	public static boolean isBookmarkKeyWithoutControlOrAlt(UserInput input, IInternalKeyMappings keyBindings) {
		return !InputModifiers.hasControlOrAlt(input.getModifiers()) &&
			IJeiKeyMappingWithExtraModifiers.matchesShortcut(keyBindings.getBookmark(), input.getKey());
	}
}

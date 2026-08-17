package mezz.jei.gui.input;

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
			keyBindings.getBookmark().matchesIgnoringModifiers(input.getKey());
	}

	public static boolean isBookmarkKeyWithoutControlOrAlt(UserInput input, IInternalKeyMappings keyBindings) {
		return !InputModifiers.hasControlOrAlt(input.getModifiers()) &&
			keyBindings.getBookmark().matchesIgnoringModifiers(input.getKey());
	}
}

package mezz.jei.gui.bookmarks.hotkeys;

public record BookmarkHotkeyAvailability(
	BookmarkHotkeyAction action,
	BookmarkHotkeySupport support
) {
}

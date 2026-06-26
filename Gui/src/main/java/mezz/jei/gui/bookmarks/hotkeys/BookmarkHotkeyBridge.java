package mezz.jei.gui.bookmarks.hotkeys;

@FunctionalInterface
public interface BookmarkHotkeyBridge {
	BookmarkHotkeySupport getSupport(
		BookmarkHotkeyAction action,
		BookmarkHotkeySupport defaultSupport,
		BookmarkHotkeyContext context
	);

	static BookmarkHotkeyBridge none() {
		return (action, defaultSupport, context) -> defaultSupport;
	}
}

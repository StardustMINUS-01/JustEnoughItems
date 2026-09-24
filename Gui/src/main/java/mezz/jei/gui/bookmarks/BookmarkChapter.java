package mezz.jei.gui.bookmarks;

public record BookmarkChapter(int id, String title, int page, boolean active) {
	public BookmarkChapter {
		if (title.isBlank())
			title = Integer.toString(id);
	}
}

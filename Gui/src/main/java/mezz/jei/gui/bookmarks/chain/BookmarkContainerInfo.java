package mezz.jei.gui.bookmarks.chain;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class BookmarkContainerInfo<C> {
	private final Map<Class<? extends C>, BookmarkContainerHandler> handlers = new HashMap<>();

	public void registerBookmarkContainerHandler(Class<? extends C> containerClass, BookmarkContainerHandler handler) {
		handlers.put(containerClass, handler);
	}

	public boolean hasBookmarkContainerHandler(Class<? extends C> containerClass) {
		return handlers.containsKey(containerClass);
	}

	public Optional<BookmarkContainerHandler> getBookmarkContainerHandler(C container) {
		if (container == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(handlers.get(container.getClass()));
	}
}

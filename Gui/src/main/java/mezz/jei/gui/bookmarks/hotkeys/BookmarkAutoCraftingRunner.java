package mezz.jei.gui.bookmarks.hotkeys;

import org.jetbrains.annotations.Nullable;

public final class BookmarkAutoCraftingRunner {
	private @Nullable BookmarkAutoCraftingBridge.Task activeTask;

	public boolean start(BookmarkAutoCraftingBridge.Task task) {
		if (activeTask != null) {
			return false;
		}
		if (!task.start()) {
			return false;
		}
		this.activeTask = task;
		return true;
	}

	public void tick() {
		if (activeTask != null && !activeTask.tick()) {
			activeTask = null;
		}
	}

	public void handleAck(int requestId, int craftedCount) {
		if (activeTask != null) {
			handleAck(activeTask.taskId(), requestId, craftedCount);
		}
	}

	public void handleAck(int taskId, int requestId, int craftedCount) {
		if (activeTask != null) {
			if (taskId != activeTask.taskId()) {
				return;
			}
			activeTask.handleAck(requestId, craftedCount);
			if (!activeTask.tick()) {
				activeTask = null;
			}
		}
	}

	public void stop() {
		activeTask = null;
	}

	public boolean hasActiveTask() {
		return activeTask != null;
	}
}

package mezz.jei.common.config;

public interface IClientToggleState {
	boolean isOverlayEnabled();

	void toggleOverlayEnabled();

	boolean isEditModeEnabled();

	void toggleEditModeEnabled();

	boolean isCheatItemsEnabled();

	void toggleCheatItemsEnabled();

	void setCheatItemsEnabled(boolean value);

	boolean isFastPickupEnabled();

	void toggleFastPickupEnabled();

	void setFastPickupEnabled(boolean value);

	boolean isBookmarkOverlayEnabled();

	void toggleBookmarkEnabled();

	void setBookmarkEnabled(boolean value);

	void addEditModeToggleListener(IEditModeListener listener);

	void clearListeners();

	interface IEditModeListener {
		void onEditModeChanged();
	}
}

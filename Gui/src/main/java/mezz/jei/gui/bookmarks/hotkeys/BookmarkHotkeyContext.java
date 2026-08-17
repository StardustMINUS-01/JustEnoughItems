package mezz.jei.gui.bookmarks.hotkeys;

public record BookmarkHotkeyContext(
	BookmarkHotkeySubject subject,
	boolean hasIngredient,
	boolean hasRecipe,
	boolean isBookmarkSlot,
	boolean isRecipeIngredient,
	boolean isGrouped,
	boolean isCraftingGroup,
	boolean isCollapsedGroup,
	boolean canTransferRecipe,
	boolean canMaxTransferRecipe,
	boolean hasBookmarkContainerHandler,
	boolean hasAutoCraftingBridge,
	boolean hasCraftingGridBridge,
	boolean hasOverlayRendererBridge
) {
	public BookmarkHotkeyContext {
		if (subject == null) {
			throw new IllegalArgumentException("subject must not be null");
		}
	}

	public static Builder builder(BookmarkHotkeySubject subject) {
		return new Builder(subject);
	}

	public static final class Builder {
		private final BookmarkHotkeySubject subject;
		private boolean hasIngredient;
		private boolean hasRecipe;
		private boolean isBookmarkSlot;
		private boolean isRecipeIngredient;
		private boolean isGrouped;
		private boolean isCraftingGroup;
		private boolean isCollapsedGroup;
		private boolean canTransferRecipe;
		private boolean canMaxTransferRecipe;
		private boolean hasBookmarkContainerHandler;
		private boolean hasAutoCraftingBridge;
		private boolean hasCraftingGridBridge;
		private boolean hasOverlayRendererBridge;

		private Builder(BookmarkHotkeySubject subject) {
			if (subject == null) {
				throw new IllegalArgumentException("subject must not be null");
			}
			this.subject = subject;
		}

		public Builder hasIngredient(boolean hasIngredient) {
			this.hasIngredient = hasIngredient;
			return this;
		}

		public Builder hasRecipe(boolean hasRecipe) {
			this.hasRecipe = hasRecipe;
			return this;
		}

		public Builder isBookmarkSlot(boolean isBookmarkSlot) {
			this.isBookmarkSlot = isBookmarkSlot;
			return this;
		}

		public Builder isRecipeIngredient(boolean isRecipeIngredient) {
			this.isRecipeIngredient = isRecipeIngredient;
			return this;
		}

		public Builder isGrouped(boolean isGrouped) {
			this.isGrouped = isGrouped;
			return this;
		}

		public Builder isCraftingGroup(boolean isCraftingGroup) {
			this.isCraftingGroup = isCraftingGroup;
			return this;
		}

		public Builder isCollapsedGroup(boolean isCollapsedGroup) {
			this.isCollapsedGroup = isCollapsedGroup;
			return this;
		}

		public Builder canTransferRecipe(boolean canTransferRecipe) {
			this.canTransferRecipe = canTransferRecipe;
			return this;
		}

		public Builder canMaxTransferRecipe(boolean canMaxTransferRecipe) {
			this.canMaxTransferRecipe = canMaxTransferRecipe;
			return this;
		}

		public Builder hasBookmarkContainerHandler(boolean hasBookmarkContainerHandler) {
			this.hasBookmarkContainerHandler = hasBookmarkContainerHandler;
			return this;
		}

		public Builder hasAutoCraftingBridge(boolean hasAutoCraftingBridge) {
			this.hasAutoCraftingBridge = hasAutoCraftingBridge;
			return this;
		}

		public Builder hasCraftingGridBridge(boolean hasCraftingGridBridge) {
			this.hasCraftingGridBridge = hasCraftingGridBridge;
			return this;
		}

		public Builder hasOverlayRendererBridge(boolean hasOverlayRendererBridge) {
			this.hasOverlayRendererBridge = hasOverlayRendererBridge;
			return this;
		}

		public BookmarkHotkeyContext build() {
			return new BookmarkHotkeyContext(
				subject,
				hasIngredient,
				hasRecipe,
				isBookmarkSlot,
				isRecipeIngredient,
				isGrouped,
				isCraftingGroup,
				isCollapsedGroup,
				canTransferRecipe,
				canMaxTransferRecipe,
				hasBookmarkContainerHandler,
				hasAutoCraftingBridge,
				hasCraftingGridBridge,
				hasOverlayRendererBridge
			);
		}
	}
}

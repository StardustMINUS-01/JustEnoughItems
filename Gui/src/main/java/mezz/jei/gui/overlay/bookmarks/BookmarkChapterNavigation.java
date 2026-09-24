package mezz.jei.gui.overlay.bookmarks;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkChapter;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;

final class BookmarkChapterNavigation implements IUserInputHandler {
	private final BookmarkOverlay overlay;
	private final Button button = Button.builder(Component.empty(), b -> {}).build();
	private ImmutableRect2i area = ImmutableRect2i.EMPTY;
	private ImmutableRect2i menu = ImmutableRect2i.EMPTY;
	private boolean open;
	private int firstRow;
	private int observedChapter = -1;
	private int lastClickedChapter = -1;
	private long lastClickTime;
	private boolean lastClickOnHeader;

	BookmarkChapterNavigation(BookmarkOverlay overlay) {
		this.overlay = overlay;
	}

	private Component label(BookmarkChapter chapter) {
		return Component.literal(chapter.title());
	}

	void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		BookmarkList list = overlay.getBookmarkList();
		if (observedChapter != list.getActiveChapterId()) {
			restorePage();
			observedChapter = list.getActiveChapterId();
		}
		list.rememberChapterPage(overlay.getContents().getPageDelegate().getPageNumber());
		area = overlay.getContents().getLeadingNavigationArea();
		if (area.isEmpty()) {
			open = false;
			return;
		}
		List<BookmarkChapter> chapters = list.getChapters();
		BookmarkChapter current = chapters.stream().filter(BookmarkChapter::active).findFirst().orElseThrow();
		button.setRectangle(area.width(), area.height(), area.x(), area.y());
		button.setMessage(label(current));
		button.render(graphics, mouseX, mouseY, partialTicks);
		if (!open)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		int rows = Math.min(8, chapters.size() + 1);
		firstRow = Math.min(firstRow, chapters.size() + 1 - rows);
		int width = Math.min(Math.max(area.width(), 110), minecraft.getWindow().getGuiScaledWidth());
		int height = rows * 18;
		int y = area.y() + area.height();
		if (y + height > minecraft.getWindow().getGuiScaledHeight())
			y = Math.max(0, area.y() - height);
		menu = new ImmutableRect2i(Math.min(area.x(), minecraft.getWindow().getGuiScaledWidth() - width), y, width, height);
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 500);
		graphics.fill(menu.x(), menu.y(), menu.x() + width, menu.y() + height, 0xFF202020);
		for (int row = 0; row < rows; row++) {
			int index = firstRow + row;
			int top = y + row * 18;
			boolean hovered = menu.contains(mouseX, mouseY) && mouseY >= top && mouseY < top + 18;
			if (hovered)
				graphics.fill(menu.x(), top, menu.x() + width, top + 18, 0xFF505050);
			String text = index == chapters.size() ? "+" : label(chapters.get(index)).getString();
			graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(text, width - 8), menu.x() + 4, top + 5,
				index < chapters.size() && chapters.get(index).active() ? 0xFFFFFF55 : 0xFFFFFFFF);
			if (hovered && index == chapters.size())
				graphics.renderTooltip(minecraft.font, Component.translatable("gui.jei.bookmark.chapter.create"), mouseX, mouseY);
		}
		graphics.pose().popPose();
	}

	private void restorePage() {
		var pages = overlay.getContents().getPageDelegate();
		int target = Math.min(overlay.getBookmarkList().getChapterPage(), Math.max(0, pages.getPageCount() - 1));
		while (pages.getPageNumber() > target && pages.previousPage()) {}
		while (pages.getPageNumber() < target && pages.nextPage()) {}
	}

	private void select(int id, double mouseX, double mouseY) {
		BookmarkList list = overlay.getBookmarkList();
		list.rememberChapterPage(overlay.getContents().getPageDelegate().getPageNumber());
		BookmarkSortDragState drag = overlay.getSortDragState();
		if (drag != null && drag.isGroupDrag()) {
			int group = list.moveGroupToChapter(drag.getSourceGroupId(), id);
			drag.continueGroupDrag(group);
		} else {
			list.selectChapter(id);
		}
		overlay.getContents().updateLayout(false);
		observedChapter = -1;
		open = false;
		BookmarkOverlay.playClickSound();
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keys) {
		if (input.getKey().getType() != InputConstants.Type.MOUSE) {
			if (open && input.getKey().getValue() == GLFW.GLFW_KEY_ESCAPE) {
				if (!input.isSimulate())
					open = false;
				return Optional.of(this);
			}
			return open ? Optional.of(this) : Optional.empty();
		}
		double x = input.getMouseX(), y = input.getMouseY();
		int click = input.getKey().getValue();
		boolean header = area.contains(x, y);
		boolean popup = open && menu.contains(x, y);
		if (!header && !open)
			return Optional.empty();
		if (input.isSimulate())
			return Optional.of(this);
		if (!header && !popup) {
			open = false;
			return Optional.of(this);
		}
		if (overlay.getSortDragState() != null || overlay.getGroupPanelDrag() != null)
			return Optional.of(this);
		List<BookmarkChapter> chapters = overlay.getBookmarkList().getChapters();
		int row = popup ? firstRow + (int) (y - menu.y()) / 18 : -1;
		BookmarkChapter chapter = row >= 0 && row < chapters.size() ? chapters.get(row) : chapters.stream().filter(BookmarkChapter::active).findFirst().orElseThrow();
		if (click == InputConstants.MOUSE_BUTTON_RIGHT) {
			if (row < chapters.size()) {
				if (overlay.getBookmarkList().deleteChapter(chapter.id())) {
					observedChapter = -1;
					overlay.getContents().updateLayout(false);
					BookmarkOverlay.playClickSound();
				}
				lastClickedChapter = -1;
			}
		} else if (click == InputConstants.MOUSE_BUTTON_LEFT) {
			long now = net.minecraft.Util.getMillis();
			if (row < chapters.size() && lastClickedChapter == chapter.id() && lastClickOnHeader == header && now - lastClickTime <= 250) {
				open = false;
				lastClickedChapter = -1;
				Minecraft.getInstance().setScreen(new RenameScreen(screen, overlay.getBookmarkList(), chapter));
				return Optional.of(this);
			}
			lastClickedChapter = row == chapters.size() ? -1 : chapter.id();
			lastClickTime = now;
			lastClickOnHeader = header;
			if (header) {
				open = !open;
				firstRow = 0;
			} else if (row >= 0 && row <= chapters.size()) {
				select(row == chapters.size() ? overlay.getBookmarkList().createChapter() : chapters.get(row).id(), x, y);
				open = row < chapters.size();
			}
		}
		return Optional.of(this);
	}

	@Override
	public Optional<IUserInputHandler> handleMouseScrolled(double x, double y, double dx, double dy) {
		if (dy == 0)
			return Optional.empty();
		List<BookmarkChapter> chapters = overlay.getBookmarkList().getChapters();
		if (open && menu.contains(x, y)) {
			firstRow = Math.max(0, Math.min(Math.max(0, chapters.size() + 1 - 8), firstRow + (dy > 0 ? -1 : 1)));
			return Optional.of(this);
		}
		if (!area.contains(x, y))
			return Optional.empty();
		var drag = overlay.getSortDragState();
		if (overlay.getGroupPanelDrag() != null || drag != null && !drag.isGroupDrag())
			return Optional.of(this);
		int current = 0;
		while (!chapters.get(current).active())
			current++;
		int next = Math.floorMod(current + (dy > 0 ? -1 : 1), chapters.size());
		if (next != current)
			select(chapters.get(next).id(), x, y);
		return Optional.of(this);
	}

	@Override
	public void unfocus() {
		open = false;
		lastClickedChapter = -1;
	}

	boolean isMenuHovered(double x, double y) {
		return overlay.isListDisplayed() && open && menu.contains(x, y);
	}

	private static final class RenameScreen extends Screen {
		private final Screen parent;
		private final BookmarkList bookmarks;
		private final BookmarkChapter chapter;
		private EditBox name;

		private RenameScreen(Screen parent, BookmarkList bookmarks, BookmarkChapter chapter) {
			super(Component.translatable("gui.jei.bookmark.chapter.rename"));
			this.parent = parent;
			this.bookmarks = bookmarks;
			this.chapter = chapter;
		}

		@Override
		protected void init() {
			int x = width / 2 - 100, y = height / 2 - 10;
			name = new EditBox(font, x, y, 200, 20, title);
			name.setMaxLength(256);
			name.setValue(chapter.title());
			addRenderableWidget(name);
			setInitialFocus(name);
			addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x, y + 26, 96, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save()).bounds(x + 104, y + 26, 96, 20).build());
		}

		private void save() {
			bookmarks.renameChapter(chapter.id(), name.getValue());
			onClose();
		}

		@Override
		public boolean keyPressed(int key, int scan, int modifiers) {
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				save();
				return true;
			}
			return super.keyPressed(key, scan, modifiers);
		}

		@Override
		public void onClose() { minecraft.setScreen(parent); }

		@Override
		public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
			renderBackground(graphics, mouseX, mouseY, partialTicks);
			graphics.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFFFF);
			super.render(graphics, mouseX, mouseY, partialTicks);
		}
	}
}

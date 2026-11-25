package mezz.jei.gui.overlay.ingredients;

import mezz.jei.api.gui.buttons.ButtonSnapshot;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.common.config.IngredientGridBackgroundStyle;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.handlers.InputGroup;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.util.ImmutablePoint2i;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.ImmutableSize2i;
import mezz.jei.gui.PageNavigation;
import mezz.jei.gui.elements.ScrollbarWidget;
import mezz.jei.gui.ghost.GhostIngredientDragManager;
import mezz.jei.gui.ghost.GhostIngredientQuickMoveManager;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IDraggableIngredientInternal;
import mezz.jei.gui.input.IPaged;
import mezz.jei.gui.overlay.IngredientListSlotContext;
import mezz.jei.gui.overlay.bookmarks.BookmarkSlotVisuals;
import mezz.jei.gui.input.InputCommands;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.util.CommandUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * Displays a list of ingredients with navigation at the top.
 */
public class IngredientGridWithNavigation implements IIngredientListOverlayContents {
	private final IngredientGridWithNavigationController controller;
	private final PageNavigation navigation;
	private final ScrollbarWidget scrollbar;
	private final IIngredientGridConfig gridConfig;
	private final IngredientGridResizer resizer;
	private @Nullable ImmutableRect2i resizeArea;
	private @Nullable VerticalAlignment resizeVerticalAlignment;
	private final IClientConfig clientConfig;
	private final IngredientGrid ingredientGrid;
	private final IIngredientGridSource ingredientSource;
	private final CommandUtil commandUtil;
	private final GhostIngredientDragManager ghostIngredientDragManager;

	public void setExtraHoveredIngredientSource(Supplier<Optional<ITypedIngredient<?>>> source) {
		this.ghostIngredientDragManager.setExtraHoveredIngredientSource(source);
	}
	private final IInputTarget inputHandler;

	private ImmutableRect2i backgroundArea = ImmutableRect2i.EMPTY;
	private ImmutableRect2i slotBackgroundArea = ImmutableRect2i.EMPTY;
	@Nullable
	private ImmutableRect2i availableArea;
	private Set<ImmutableRect2i> guiExclusionAreas = Set.of();
	@Nullable
	private ImmutablePoint2i mouseExclusionPoint;
	private boolean active;
	private boolean layoutDirty;
	private boolean leadingNavigationContent;

	public void enableLeadingNavigationContent() {
		leadingNavigationContent = true;
		navigation.setLeadingContent(true);
		markLayoutDirty();
	}

	public ImmutableRect2i getLeadingNavigationArea() {
		updateLayoutIfDirty();
		return navigation.getLeadingArea();
	}

	public IngredientGridWithNavigation(
		String debugName,
		IIngredientGridSource ingredientSource,
		IngredientGrid ingredientGrid,
		IClientToggleState toggleState,
		IClientConfig clientConfig,
		IConnectionToServer serverConnection,
		IIngredientGridConfig gridConfig,
		IScreenHelper screenHelper,
		IIngredientManager ingredientManager
	) {
		this.ingredientGrid = ingredientGrid;
		this.ingredientSource = ingredientSource;
		this.gridConfig = gridConfig;
		this.resizer = new IngredientGridResizer(this, gridConfig, clientConfig);
		this.clientConfig = clientConfig;
		this.commandUtil = new CommandUtil(clientConfig, serverConnection);
		this.ghostIngredientDragManager = new GhostIngredientDragManager(this.ingredientGrid, screenHelper, ingredientManager, toggleState);
		GhostIngredientQuickMoveManager ghostIngredientQuickMoveManager = new GhostIngredientQuickMoveManager(this.ingredientGrid, screenHelper);
		this.controller = new IngredientGridWithNavigationController(
			ingredientSource,
			this.ingredientGrid,
			gridConfig,
			toggleState,
			clientConfig,
			this.commandUtil,
			ingredientManager,
			this::isMouseOver,
			ghostIngredientQuickMoveManager
		);
		this.navigation = new PageNavigation(this.controller, false);
		this.scrollbar = new ScrollbarWidget(this.controller);
		this.controller.setOnLayoutChanged(this.navigation::updatePageNumber);
		this.inputHandler = new InputGroup(
			debugName,
			this.scrollbar,
			this.controller,
			this.navigation.createInputHandler()
		);

		this.ingredientSource.addSourceListChangedListener(this::markLayoutDirty);
		addGridConfigListeners(gridConfig);
		Internal.registerRuntimeListenerRemoval(clientConfig.guiResizeEnabled().addListener(v -> resizer.resetInput()));
		Internal.registerRuntimeListenerRemoval(clientConfig.smoothScrollingEnabled().addListener(v -> markLayoutDirty()));
	}

	private void addGridConfigListeners(IIngredientGridConfig gridConfig) {
		Internal.registerRuntimeListenerRemoval(gridConfig.maxColumns().addListener(v -> markLayoutDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.maxRows().addListener(v -> markLayoutDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.backgroundStyle().addListener(v -> markLayoutDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.layoutMode().addListener(v -> markLayoutDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.horizontalAlignment().addListener(v -> markLayoutDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.verticalAlignment().addListener(v -> markLayoutDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.navigationVisibility().addListener(v -> markLayoutDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.navigationMode().addListener(v -> markLayoutDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.shrinkToFit().addListener(v -> markLayoutDirty()));
	}

	private void markLayoutDirty() {
		this.layoutDirty = true;
	}

	public void setResizeBounds(ImmutableRect2i area, VerticalAlignment verticalAlignment) {
		this.resizeArea = area;
		this.resizeVerticalAlignment = verticalAlignment;
	}

	VerticalAlignment getResizeVerticalAlignment() {
		if (resizeVerticalAlignment != null) {
			return resizeVerticalAlignment;
		}
		return gridConfig.verticalAlignment().get();
	}

	@Override
	public IInputTarget getResizeInputHandler() {
		return resizer;
	}

	boolean isResizeExcluded(double mouseX, double mouseY) {
		return guiExclusionAreas.stream().anyMatch(area -> area.contains(mouseX, mouseY)) ||
			(mouseExclusionPoint != null && mouseExclusionPoint.x() == (int) mouseX && mouseExclusionPoint.y() == (int) mouseY);
	}

	ImmutableSize2i getMaximumResizeSize() {
		ImmutableRect2i area = resizeArea;
		if (area == null) {
			area = availableArea;
		}
		if (area == null) {
			return ImmutableSize2i.EMPTY;
		}
		ImmutableRect2i gridArea = ingredientGrid.getArea();
		int margin = 2 * IngredientGridWithNavigationLayout.BORDER_MARGIN;
		return new ImmutableSize2i(
			Math.max(0, area.width() - margin - (backgroundArea.width() - gridArea.width())),
			Math.max(0, area.height() - margin - (backgroundArea.height() - gridArea.height()))
		);
	}

	private void updateLayoutIfDirty() {
		if (this.layoutDirty && this.availableArea != null) {
			updateBounds(this.availableArea, this.guiExclusionAreas, this.mouseExclusionPoint);
			this.controller.updateLayoutKeepingPageNumber();
		}
	}

	@Override
	public boolean hasRoom() {
		updateLayoutIfDirty();
		return this.active;
	}

	@Override
	public void updateLayoutToFirstPage() {
		this.controller.updateLayoutToFirstPage();
	}

	public void updateLayout(boolean resetToFirstPage) {
		if (resetToFirstPage) {
			updateLayoutToFirstPage();
		} else {
			this.controller.updateLayoutKeepingPageNumber();
		}
	}

	public void refreshLayoutAfterSourceChange(@Nullable IElement<?> anchor) {
		if (availableArea != null) {
			updateBounds(availableArea, guiExclusionAreas, mouseExclusionPoint);
		}
		if (anchor == null) {
			controller.updateLayoutKeepingPageNumber();
		} else {
			controller.updateLayoutKeepingPageAnchorVisible(anchor);
		}
	}

	@Override
	public void updateLayoutKeepingPageAnchorVisible(@Nullable IElement<?> pageAnchorElement) {
		this.controller.updateLayoutKeepingPageAnchorVisible(pageAnchorElement);
	}

	public void setPageAnchorElement(IElement<?> pageAnchorElement) {
		this.controller.setPageAnchorElement(pageAnchorElement);
	}

	public List<IElement<?>> getPageElements() {
		updateLayoutIfDirty();
		return this.controller.getPageElements();
	}

	@Override
	public @Nullable IElement<?> getPageAnchorElement() {
		return this.controller.getPageAnchorElement();
	}

	@Override
	public void updateBounds(final ImmutableRect2i availableArea, Set<ImmutableRect2i> guiExclusionAreas, @Nullable ImmutablePoint2i mouseExclusionPoint) {
		this.availableArea = availableArea;
		this.guiExclusionAreas = guiExclusionAreas;
		this.mouseExclusionPoint = mouseExclusionPoint;
		this.layoutDirty = false;
		IngredientGridWithNavigationLayout layout = calculateLayout(
			availableArea,
			guiExclusionAreas,
			this.ingredientSource.getElements().size()
		);
		applyLayout(layout, guiExclusionAreas, mouseExclusionPoint);
	}

	public void updateBounds(
		final ImmutableRect2i availableArea,
		OptionalInt bottomLimit,
		Set<ImmutableRect2i> guiExclusionAreas,
		@Nullable ImmutablePoint2i mouseExclusionPoint
	) {
		ImmutableRect2i limitedArea = availableArea;
		if (bottomLimit.isPresent()) {
			int bottom = Math.min(availableArea.getY() + availableArea.getHeight(), bottomLimit.getAsInt());
			limitedArea = new ImmutableRect2i(
				availableArea.getX(),
				availableArea.getY(),
				availableArea.getWidth(),
				Math.max(0, bottom - availableArea.getY())
			);
		}
		updateBounds(limitedArea, guiExclusionAreas, mouseExclusionPoint);
	}

	private IngredientGridWithNavigationLayout calculateLayout(
		final ImmutableRect2i availableArea,
		Set<ImmutableRect2i> guiExclusionAreas,
		int ingredientCount
	) {
		if (ingredientSource instanceof IProjectedIngredientGridSource projectedSource) {
			java.util.function.ToIntFunction<ImmutableRect2i> slotCount = area -> {
				if (guiExclusionAreas.isEmpty() && mouseExclusionPoint == null) {
					return projectedSource.getProjectedSlotCount(area.width() / IngredientGridLayout.INGREDIENT_WIDTH, List.of());
				}
				List<Integer> rows = IngredientGridLayout.getUsableColumnsPerRow(area, guiExclusionAreas, mouseExclusionPoint);
				int columns = rows.isEmpty() ? 0 : rows.getFirst();
				// Uniform rows have the same projection at every height, so reuse its cached layout.
				return projectedSource.getProjectedSlotCount(columns, rows.stream().allMatch(width -> width == columns) ? List.of() : rows);
			};
			boolean reserveNavigation = leadingNavigationContent && gridConfig.navigationVisibility().get() != mezz.jei.common.config.NavigationVisibility.DISABLED;
			if (gridConfig.navigationMode().get().usesScrollbar()) {
				return IngredientGridScrollbarLayout.calculateProjected(gridConfig, availableArea, guiExclusionAreas, slotCount, isSmoothScrolling(), reserveNavigation);
			}
			return IngredientGridButtonNavigationLayout.calculateProjected(gridConfig, availableArea, guiExclusionAreas, slotCount, reserveNavigation);
		}
		if (this.gridConfig.navigationMode().get().usesScrollbar()) {
			if (leadingNavigationContent && gridConfig.navigationVisibility().get() != mezz.jei.common.config.NavigationVisibility.DISABLED)
				return IngredientGridScrollbarLayout.calculateWithNavigation(gridConfig, availableArea, guiExclusionAreas, ingredientCount, isSmoothScrolling());
			return IngredientGridScrollbarLayout.calculate(
				this.gridConfig,
				availableArea,
				guiExclusionAreas,
				ingredientCount,
				isSmoothScrolling()
			);
		}

		if (leadingNavigationContent && gridConfig.navigationVisibility().get() != mezz.jei.common.config.NavigationVisibility.DISABLED)
			return IngredientGridButtonNavigationLayout.calculateWithNavigation(gridConfig, availableArea, guiExclusionAreas, ingredientCount);
		return IngredientGridButtonNavigationLayout.calculate(
			this.gridConfig,
			availableArea,
			guiExclusionAreas,
			ingredientCount
		);
	}

	private void applyLayout(
		IngredientGridWithNavigationLayout layout,
		Set<ImmutableRect2i> guiExclusionAreas,
		@Nullable ImmutablePoint2i mouseExclusionPoint
	) {
		this.guiExclusionAreas = guiExclusionAreas;
		if (!layout.hasRoom()) {
			clearLayout();
			return;
		}

		this.ingredientGrid.updateBounds(
			layout.ingredientGridArea(),
			guiExclusionAreas,
			mouseExclusionPoint
		);
		this.slotBackgroundArea = layout.slotBackgroundArea();
		this.navigation.updateBounds(layout.navigationArea());
		this.scrollbar.updateBounds(layout.scrollbarArea());
		this.backgroundArea = layout.backgroundArea();
		this.active = true;
	}

	private void clearLayout() {
		this.resizer.resetInput();
		this.ingredientGrid.updateBounds(ImmutableRect2i.EMPTY, Set.of(), null);
		this.slotBackgroundArea = ImmutableRect2i.EMPTY;
		this.navigation.updateBounds(ImmutableRect2i.EMPTY);
		this.scrollbar.updateBounds(ImmutableRect2i.EMPTY);
		this.backgroundArea = ImmutableRect2i.EMPTY;
		this.active = false;
	}

	@Override
	public ImmutableRect2i getBackgroundArea() {
		updateLayoutIfDirty();
		return this.backgroundArea;
	}

	@Override
	public ImmutableRect2i getIngredientGridArea() {
		updateLayoutIfDirty();
		return this.ingredientGrid.getArea();
	}

	@Override
	public boolean isBackgroundEnabled() {
		return this.gridConfig.backgroundStyle().get().isEnabled();
	}

	@Override
	public ImmutableRect2i getSlotBackgroundArea() {
		updateLayoutIfDirty();
		return this.slotBackgroundArea;
	}

	@Override
	public int getPageIndex() {
		updateLayoutIfDirty();
		if (!active) {
			return 0;
		}
		return controller.getPageNumber();
	}

	@Override
	public int getPageCount() {
		updateLayoutIfDirty();
		if (!active) {
			return 0;
		}
		return controller.getPageCount();
	}

	@Override
	public ImmutableRect2i getNextPageButtonArea() {
		updateLayoutIfDirty();
		return this.navigation.getNextButtonArea();
	}

	@Override
	public ButtonSnapshot getNextButton(boolean visible) {
		updateLayoutIfDirty();
		return navigation.getNextButton(active && visible);
	}

	@Override
	public ImmutableRect2i getBackButtonArea() {
		updateLayoutIfDirty();
		return this.navigation.getBackButtonArea();
	}

	@Override
	public ButtonSnapshot getBackButton(boolean visible) {
		updateLayoutIfDirty();
		return navigation.getBackButton(active && visible);
	}

	public IPaged getPageDelegate() {
		return controller;
	}

	public void setNavigationButton(mezz.jei.gui.elements.IconButton button) {
		this.navigation.setExtraButton(button);
	}

	public int getFirstItemIndex() {
		return this.controller.getFirstItemIndex();
	}

	public void scrollByPixels(double pixels) {
		updateLayoutIfDirty();
		this.controller.scrollByPixels(pixels);
	}

	private boolean isSmoothScrolling() {
		return this.gridConfig.navigationMode().get().usesScrollbar() &&
			this.clientConfig.smoothScrollingEnabled().get();
	}

	public void setPageButtonsForcePressed(boolean nextButton, boolean backButton) {
		this.navigation.setForcePressed(nextButton, backButton);
	}

	@Override
	public void drawForeground(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		updateLayoutIfDirty();
		if (!this.active) {
			return;
		}
		boolean drawSlotBackgrounds = this.gridConfig.backgroundStyle().get() == IngredientGridBackgroundStyle.GRID;
		this.ingredientGrid.draw(minecraft, guiGraphics, mouseX, mouseY, drawSlotBackgrounds);
		this.scrollbar.draw(guiGraphics, mouseX, mouseY);
		this.navigation.draw(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
	}

	@Override
	public void drawTooltips(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY) {
		updateLayoutIfDirty();
		if (!this.active) {
			return;
		}
		this.ghostIngredientDragManager.drawTooltips(minecraft, guiGraphics, mouseX, mouseY);
		if (this.resizer.isMouseOver(mouseX, mouseY)) {
			return;
		}
		this.ingredientGrid.drawTooltips(minecraft, guiGraphics, mouseX, mouseY);
		this.navigation.drawTooltips(guiGraphics, mouseX, mouseY);
	}

	@Override
	public void tick() {
		if (!this.active) {
			return;
		}
		this.ingredientGrid.tick();
	}

	public boolean isMouseOver(double mouseX, double mouseY) {
		updateLayoutIfDirty();
		return this.active &&
			(this.backgroundArea.contains(mouseX, mouseY) || this.resizer.isOverResizeHandle(mouseX, mouseY)) &&
			this.guiExclusionAreas.stream()
				.noneMatch(area -> area.contains(mouseX, mouseY));
	}

	@Override
	public IInputTarget createDeleteItemInputHandler() {
		return this.ingredientGrid.getInputHandler();
	}

	@Override
	public IInputTarget createInputHandler() {
		return this.inputHandler;
	}

	@Override
	public GuiEventListener createKeyboardNavigation(BooleanSupplier active) {
		return new IngredientGridKeyboardNavigation(ingredientGrid, controller, active);
	}

	@Override
	public boolean selectKeyboardIngredient(IElement<?> ingredient) {
		if (!ingredientSource.getElements().contains(ingredient)) {
			return false;
		}
		updateLayoutIfDirty();
		controller.updateLayoutKeepingPageAnchorVisible(ingredient);
		ingredientGrid.select(ingredient);
		return ingredientGrid.getFocusedSlot().isPresent();
	}

	@Override
	public void registerInputCommands(InputCommands commands, IInternalKeyMappings keys, BooleanSupplier active) {
		controller.registerInputCommands(commands, keys, active);
	}

	@Override
	public Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
		updateLayoutIfDirty();
		if (!this.active || this.resizer.isMouseOver(mouseX, mouseY)) {
			return Stream.empty();
		}
		return this.ingredientGrid.getIngredientUnderMouse(mouseX, mouseY)
			.map(this.controller::createPageAnchorIngredient);
	}

	@Override
	public Stream<IClickableIngredientInternal<?>> getKeyboardFocusedIngredients() {
		updateLayoutIfDirty();
		if (!this.active) {
			return Stream.empty();
		}
		return this.ingredientGrid.getFocusedSlot().stream()
			.flatMap(slot -> slot.getClickableIngredient().stream())
			.map(this.controller::createPageAnchorIngredient);
	}

	@Override
	public Stream<IDraggableIngredientInternal<?>> getDraggableIngredientUnderMouse(double mouseX, double mouseY) {
		updateLayoutIfDirty();
		if (!this.active || this.resizer.isMouseOver(mouseX, mouseY)) {
			return Stream.empty();
		}
		return this.ingredientGrid.getDraggableIngredientUnderMouse(mouseX, mouseY);
	}

	@Override
	public <T> Stream<T> getVisibleIngredients(IIngredientType<T> ingredientType) {
		updateLayoutIfDirty();
		if (!this.active) {
			return Stream.empty();
		}
		return this.ingredientGrid.getVisibleIngredients(ingredientType);
	}

	@Override
	public boolean isEmpty() {
		return this.ingredientSource.getElements().isEmpty();
	}

	@Override
	public void close() {
		// Discard the previous screen's area so source changes cannot reopen a closed grid.
		this.availableArea = null;
		clearLayout();
		this.ghostIngredientDragManager.stopDrag();
	}

	@Override
	public void drawOnForeground(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		updateLayoutIfDirty();
		if (!this.active) {
			return;
		}
		this.ghostIngredientDragManager.drawOnForeground(guiGraphics, mouseX, mouseY);
	}

	@Override
	public Optional<IInputInteraction> beginDrag(Screen screen, UserInput input) {
		return this.ghostIngredientDragManager.beginDrag(screen, input);
	}

	public int size() {
		updateLayoutIfDirty();
		if (!this.active) {
			return 0;
		}
		return this.ingredientGrid.size();
	}

	public Stream<IngredientListSlot> getSlots() {
		updateLayoutIfDirty();
		if (!this.active) {
			return Stream.empty();
		}
		return this.ingredientGrid.getSlots();
	}

	public List<IngredientListSlot> getAllSlots() {
		updateLayoutIfDirty();
		if (!this.active) {
			return List.of();
		}
		return this.ingredientGrid.getAllSlots();
	}

	public void setSlotVisualsResolver(Function<IngredientListSlotContext, Optional<BookmarkSlotVisuals>> slotVisualsResolver) {
		this.ingredientGrid.setSlotVisualsResolver(slotVisualsResolver);
	}

	public int getUsableColumnCount() {
		updateLayoutIfDirty();
		return this.ingredientGrid.getUsableColumnCount();
	}

	public List<Integer> getUsableColumnsPerRow() {
		updateLayoutIfDirty();
		return this.ingredientGrid.getUsableColumnsPerRow();
	}
}

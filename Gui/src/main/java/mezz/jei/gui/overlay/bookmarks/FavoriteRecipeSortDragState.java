package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.SafeIngredientUtil;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.favorites.FavoriteRecipePanelState;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.overlay.ingredients.IngredientGrid;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FavoriteRecipeSortDragState {
	private static final int DRAG_OVERLAY_COLOR = 0x66555555;

	private final Kind kind;
	private final FocusedRecipe sourceRecipe;
	private final @Nullable FavoriteRecipePanelState.RecipeInputKey sourceInputKey;
	private final ImmutableRect2i sourceArea;
	private final int dragOffsetX;
	private final int dragOffsetY;
	private final long startedAtMillis;
	private final long thresholdMillis;
	private List<PreviewSlot> previewSlots = List.of();
	private List<ImmutableRect2i> sourceSlotOverlayAreas = List.of();
	private List<ImmutableRect2i> targetSlotOverlayAreas = List.of();
	private boolean active;

	private FavoriteRecipeSortDragState(
		Kind kind,
		FocusedRecipe sourceRecipe,
		@Nullable FavoriteRecipePanelState.RecipeInputKey sourceInputKey,
		ImmutableRect2i sourceArea,
		double mouseX,
		double mouseY,
		long startedAtMillis,
		long thresholdMillis
	) {
		this.kind = kind;
		this.sourceRecipe = sourceRecipe;
		this.sourceInputKey = sourceInputKey;
		this.sourceArea = sourceArea;
		this.dragOffsetX = sourceArea.getX() - (int) Math.round(mouseX);
		this.dragOffsetY = sourceArea.getY() - (int) Math.round(mouseY);
		this.startedAtMillis = startedAtMillis;
		this.thresholdMillis = thresholdMillis;
		this.active = !sourceArea.contains(mouseX, mouseY);
	}

	public static FavoriteRecipeSortDragState recipe(
		FocusedRecipe sourceRecipe,
		ImmutableRect2i sourceArea,
		double mouseX,
		double mouseY,
		long startedAtMillis,
		long thresholdMillis
	) {
		return new FavoriteRecipeSortDragState(
			Kind.RECIPE,
			sourceRecipe,
			null,
			sourceArea,
			mouseX,
			mouseY,
			startedAtMillis,
			thresholdMillis
		);
	}

	public static FavoriteRecipeSortDragState input(
		FocusedRecipe sourceRecipe,
		FavoriteRecipePanelState.RecipeInputKey sourceInputKey,
		ImmutableRect2i sourceArea,
		double mouseX,
		double mouseY,
		long startedAtMillis,
		long thresholdMillis
	) {
		return new FavoriteRecipeSortDragState(
			Kind.INPUT,
			sourceRecipe,
			sourceInputKey,
			sourceArea,
			mouseX,
			mouseY,
			startedAtMillis,
			thresholdMillis
		);
	}

	public boolean isActive() {
		return active;
	}

	public void stop() {
		previewSlots = List.of();
		sourceSlotOverlayAreas = List.of();
		targetSlotOverlayAreas = List.of();
		active = false;
	}

	public boolean update(
		FavoriteRecipeStore store,
		FavoriteRecipePanelState panelState,
		List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> panelSlots,
		double mouseX,
		double mouseY,
		long nowMillis
	) {
		if (!active) {
			active = !sourceArea.contains(mouseX, mouseY) || nowMillis - startedAtMillis >= thresholdMillis;
			if (!active) {
				return false;
			}
		}
		updatePreview(panelSlots);
		boolean changedHiddenState = updateHiddenState(panelState);
		boolean changedOrder = switch (kind) {
			case RECIPE -> updateRecipe(store, panelSlots, mouseY);
			case INPUT -> updateInput(panelState, panelSlots, mouseX, mouseY);
		};
		return changedHiddenState || changedOrder;
	}

	private boolean updateHiddenState(FavoriteRecipePanelState panelState) {
		return switch (kind) {
			case RECIPE -> panelState.setSortDragHiddenRecipe(sourceRecipe);
			case INPUT -> sourceInputKey != null && panelState.setSortDragHiddenInput(sourceRecipe, sourceInputKey);
		};
	}

	public boolean drawSourceSlotOverlays(GuiGraphics guiGraphics) {
		for (ImmutableRect2i area : sourceSlotOverlayAreas) {
			guiGraphics.fill(
				area.getX(),
				area.getY(),
				area.getX() + area.getWidth(),
				area.getY() + area.getHeight(),
				DRAG_OVERLAY_COLOR
			);
		}
		return !sourceSlotOverlayAreas.isEmpty();
	}

	public boolean drawTargetSlotOverlays(GuiGraphics guiGraphics) {
		for (ImmutableRect2i area : targetSlotOverlayAreas) {
			IngredientGrid.drawHighlight(guiGraphics, area);
		}
		return !targetSlotOverlayAreas.isEmpty();
	}

	public void updateProjectedSlotOverlayAreas(List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> projectedPanelSlots) {
		if (!active) {
			targetSlotOverlayAreas = List.of();
			return;
		}
		targetSlotOverlayAreas = getSourceSlots(projectedPanelSlots).stream()
			.map(BookmarkPanelLayout.PanelSlot::area)
			.toList();
	}

	public boolean drawDraggedItems(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (!active || previewSlots.isEmpty()) {
			return false;
		}
		IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
		for (PreviewSlot slot : previewSlots) {
			ITypedIngredient<?> typedIngredient = slot.element().getTypedIngredient();
			drawIngredient(
				guiGraphics,
				ingredientManager,
				typedIngredient,
				mouseX + dragOffsetX + slot.relativeX(),
				mouseY + dragOffsetY + slot.relativeY()
			);
		}
		return true;
	}

	private boolean updateRecipe(
		FavoriteRecipeStore store,
		List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> panelSlots,
		double mouseY
	) {
		Optional<FavoriteRecipeMovePlan> movePlan = FavoriteRecipeMovePlan.create(toRecipePanelSlots(panelSlots), sourceRecipe, mouseY);
		if (movePlan.isEmpty()) {
			targetSlotOverlayAreas = List.of();
			return false;
		}

		FavoriteRecipeMovePlan plan = movePlan.get();
		targetSlotOverlayAreas = plan.targetAreas();
		return store.moveFavorite(sourceRecipe, plan.targetRecipe(), plan.offset());
	}

	private boolean updateInput(
		FavoriteRecipePanelState panelState,
		List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> panelSlots,
		double mouseX,
		double mouseY
	) {
		if (sourceInputKey == null) {
			targetSlotOverlayAreas = List.of();
			return false;
		}
		List<FavoriteRecipePanelState.RecipeInputKey> inputOrder = getInputOrder(panelSlots);
		Optional<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> targetSlot = panelSlots.stream()
			.filter(slot -> slot.area().contains(mouseX, mouseY))
			.filter(slot -> sourceRecipe.equals(slot.item().getFocusedRecipe()))
			.filter(slot -> !slot.item().isFavoriteTarget())
			.filter(slot -> slot.item().getRecipeInputKey().isPresent())
			.findFirst();
		if (targetSlot.isEmpty()) {
			targetSlotOverlayAreas = List.of();
			return false;
		}
		FavoriteRecipePanelState.RecipeInputKey targetInputKey = targetSlot.get().item().getRecipeInputKey().orElseThrow();
		if (sourceInputKey.equals(targetInputKey)) {
			targetSlotOverlayAreas = List.of();
			return false;
		}
		int sourceIndex = inputOrder.indexOf(sourceInputKey);
		int targetIndex = inputOrder.indexOf(targetInputKey);
		if (sourceIndex < 0 || targetIndex < 0) {
			targetSlotOverlayAreas = List.of();
			return false;
		}
		targetSlotOverlayAreas = List.of(targetSlot.get().area());
		int offset = sourceIndex < targetIndex ? 1 : 0;
		return panelState.moveRecipeInput(sourceRecipe, inputOrder, sourceInputKey, targetInputKey, offset);
	}

	private void updatePreview(List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> panelSlots) {
		List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> sourceSlots = getSourceSlots(panelSlots);
		sourceSlotOverlayAreas = sourceSlots.stream()
			.map(BookmarkPanelLayout.PanelSlot::area)
			.toList();
		if (!previewSlots.isEmpty() || sourceSlots.isEmpty()) {
			return;
		}
		ImmutableRect2i origin = getPreviewOrigin(sourceSlots);
		previewSlots = sourceSlots.stream()
			.map(slot -> new PreviewSlot(
				slot.item(),
				slot.area().getX() - origin.getX(),
				slot.area().getY() - origin.getY()
			))
			.toList();
	}

	private List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> getSourceSlots(
		List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> panelSlots
	) {
		return switch (kind) {
			case RECIPE -> panelSlots.stream()
				.filter(slot -> sourceRecipe.equals(slot.item().getFocusedRecipe()))
				.toList();
			case INPUT -> panelSlots.stream()
				.filter(slot -> sourceRecipe.equals(slot.item().getFocusedRecipe()))
				.filter(slot -> slot.item().getRecipeInputKey().filter(key -> key.equals(sourceInputKey)).isPresent())
				.toList();
		};
	}

	private ImmutableRect2i getPreviewOrigin(List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> sourceSlots) {
		if (kind == Kind.INPUT) {
			return sourceArea;
		}
		return sourceSlots.stream()
			.map(BookmarkPanelLayout.PanelSlot::area)
			.min(Comparator.comparingInt(ImmutableRect2i::getY).thenComparingInt(ImmutableRect2i::getX))
			.orElse(sourceArea);
	}

	private List<FavoriteRecipePanelState.RecipeInputKey> getInputOrder(
		List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> panelSlots
	) {
		Set<FavoriteRecipePanelState.RecipeInputKey> seen = new HashSet<>();
		return panelSlots.stream()
			.sorted(Comparator.comparingInt((BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>> slot) -> slot.area().getY())
				.thenComparingInt(slot -> slot.area().getX()))
			.map(BookmarkPanelLayout.PanelSlot::item)
			.filter(element -> sourceRecipe.equals(element.getFocusedRecipe()))
			.filter(element -> !element.isFavoriteTarget())
			.map(FavoriteRecipeElement::getRecipeInputKey)
			.flatMap(Optional::stream)
			.filter(seen::add)
			.toList();
	}

	private static List<BookmarkPanelLayout.PanelSlot<FocusedRecipe>> toRecipePanelSlots(
		List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> panelSlots
	) {
		return panelSlots.stream()
			.map(slot -> new BookmarkPanelLayout.PanelSlot<>(
				slot.item().getFocusedRecipe(),
				slot.groupId(),
				slot.area(),
				slot.shadow(),
				slot.item().getFocusedRecipe()
			))
			.toList();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <T> void drawIngredient(
		GuiGraphics guiGraphics,
		IIngredientManager ingredientManager,
		ITypedIngredient<T> typedIngredient,
		int x,
		int y
	) {
		IIngredientType<T> type = typedIngredient.getType();
		IIngredientRenderer<T> renderer = ingredientManager.getIngredientRenderer(type);
		SafeIngredientUtil.render(guiGraphics, renderer, typedIngredient, x, y);
	}

	private record PreviewSlot(FavoriteRecipeElement<?> element, int relativeX, int relativeY) {
	}

	public List<FloatingGroupPanelSlot> getFloatingGroupPanelSlots(int mouseX, int mouseY) {
		if (kind != Kind.RECIPE || !active || previewSlots.isEmpty()) {
			return List.of();
		}
		Map<Integer, PreviewSlot> rowsByY = new LinkedHashMap<>();
		previewSlots.stream()
			.sorted(Comparator.comparingInt(PreviewSlot::relativeY).thenComparingInt(PreviewSlot::relativeX))
			.forEach(slot -> rowsByY.putIfAbsent(slot.relativeY(), slot));
		List<PreviewSlot> rowSlots = new ArrayList<>(rowsByY.values());
		List<FloatingGroupPanelSlot> groupPanelSlots = new ArrayList<>();
		for (int i = 0; i < rowSlots.size(); i++) {
			PreviewSlot slot = rowSlots.get(i);
			boolean connectedToPrevious = i > 0 && rowSlots.get(i - 1).relativeY() + sourceArea.getHeight() == slot.relativeY();
			boolean connectedToNext = i + 1 < rowSlots.size() && slot.relativeY() + sourceArea.getHeight() == rowSlots.get(i + 1).relativeY();
			groupPanelSlots.add(new FloatingGroupPanelSlot(
				new ImmutableRect2i(
					mouseX + dragOffsetX + slot.relativeX(),
					mouseY + dragOffsetY + slot.relativeY(),
					sourceArea.getWidth(),
					sourceArea.getHeight()
				),
				connectedToPrevious,
				connectedToNext
			));
		}
		return groupPanelSlots;
	}

	public record FloatingGroupPanelSlot(ImmutableRect2i slotArea, boolean connectedToPrevious, boolean connectedToNext) {
	}

	private enum Kind {
		RECIPE,
		INPUT
	}
}

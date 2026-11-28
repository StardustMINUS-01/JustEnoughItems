package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.favorites.FavoriteRecipePanelState;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.overlay.ingredients.IngredientGrid;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class FavoriteRecipeSortDragState {
	private final Kind kind;
	private final FocusedRecipe sourceRecipe;
	private final @Nullable FavoriteRecipePanelState.RecipeInputKey sourceInputKey;
	private final ImmutableRect2i sourceArea;
	private final int dragOffsetX;
	private final int dragOffsetY;
	private final long startedAtMillis;
	private final long thresholdMillis;
	private List<BookmarkSortDragState.PreviewSlot<FavoriteRecipeElement<?>>> previewSlots = List.of();
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
		return BookmarkSortDragState.drawSlotOverlays(guiGraphics, sourceSlotOverlayAreas);
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
		return active && BookmarkSortDragState.drawPreviewSlots(guiGraphics, previewSlots, mouseX + dragOffsetX, mouseY + dragOffsetY,
			FavoriteRecipeElement::getTypedIngredient);
	}

	private boolean updateRecipe(
		FavoriteRecipeStore store,
		List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> panelSlots,
		double mouseY
	) {
		return FavoriteRecipeMovePlan.create(panelSlots, sourceRecipe, mouseY)
			.map(plan -> store.moveFavorite(sourceRecipe, plan.targetRecipe(), plan.offset()))
			.orElse(false);
	}

	private boolean updateInput(
		FavoriteRecipePanelState panelState,
		List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> panelSlots,
		double mouseX,
		double mouseY
	) {
		if (sourceInputKey == null) {
			return false;
		}
		Optional<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> targetSlot = panelSlots.stream()
			.filter(slot -> slot.area().contains(mouseX, mouseY))
			.filter(slot -> sourceRecipe.equals(slot.item().getFocusedRecipe()))
			.filter(slot -> !slot.item().isFavoriteTarget())
			.filter(slot -> slot.item().getRecipeInputKey().isPresent())
			.findFirst();
		if (targetSlot.isEmpty()) {
			return false;
		}
		FavoriteRecipePanelState.RecipeInputKey targetInputKey = targetSlot.get().item().getRecipeInputKey().orElseThrow();
		if (sourceInputKey.equals(targetInputKey)) {
			return false;
		}
		var inputOrder = getInputOrder(panelSlots);
		int sourceIndex = inputOrder.indexOf(sourceInputKey);
		int targetIndex = inputOrder.indexOf(targetInputKey);
		if (sourceIndex < 0 || targetIndex < 0) {
			return false;
		}
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
		ImmutableRect2i origin = kind == Kind.INPUT ? sourceArea : sourceSlots.stream()
			.min(BookmarkPanelLayout.SLOT_ORDER).orElseThrow().area();
		previewSlots = BookmarkSortDragState.toPreviewSlots(sourceSlots, origin);
	}

	private List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> getSourceSlots(
		List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> panelSlots
	) {
		return panelSlots.stream()
			.filter(slot -> sourceRecipe.equals(slot.item().getFocusedRecipe()))
			.filter(slot -> kind == Kind.RECIPE || slot.item().getRecipeInputKey().filter(key -> key.equals(sourceInputKey)).isPresent())
			.toList();
	}

	private List<FavoriteRecipePanelState.RecipeInputKey> getInputOrder(
		List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> panelSlots
	) {
		return panelSlots.stream()
			.filter(slot -> sourceRecipe.equals(slot.item().getFocusedRecipe()) && !slot.item().isFavoriteTarget())
			.sorted(BookmarkPanelLayout.SLOT_ORDER)
			.map(slot -> slot.item().getRecipeInputKey())
			.flatMap(Optional::stream)
			.distinct()
			.toList();
	}

	public List<BookmarkSortDragState.FloatingGroupPanelSlot> getFloatingGroupPanelSlots(int mouseX, int mouseY) {
		if (kind != Kind.RECIPE || !active || previewSlots.isEmpty()) {
			return List.of();
		}
		return BookmarkSortDragState.getFloatingGroupPanelSlots(
			previewSlots,
			mouseX,
			mouseY,
			dragOffsetX,
			dragOffsetY
		);
	}

	private enum Kind {
		RECIPE,
		INPUT
	}
}

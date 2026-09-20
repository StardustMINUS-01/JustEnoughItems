package mezz.jei.gui.bookmarks.tree;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.bookmarks.BookmarkCandidateTooltipHelper;
import mezz.jei.gui.bookmarks.BookmarkCandidateTooltipState;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkRecipeSelection;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.recipes.IIngredientCandidateSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** A bookmark-backed recipe region, without the recipe page's navigation or transfer buttons. */
public final class RecipeTreePreview {
	private final IRecipeLayoutDrawable<?> layout;
	private final BookmarkRecipeSelection selection;

	public RecipeTreePreview(IRecipeLayoutDrawable<?> layout, List<RecipeChainInput> saved, IIngredientManager manager,
		Map<Integer, BookmarkIngredientKey> previousChoices) {
		this.layout = layout;
		layout.setPosition(0, 0);
		var border = layout.getRectWithBorder();
		layout.setPosition(-border.getX(), -border.getY());
		selection = new BookmarkRecipeSelection(layout, saved, manager, previousChoices);
	}

	public boolean showCandidates(GuiGraphics graphics, Area area, int x, int y,
		BookmarkCandidateTooltipState state, BookmarkList bookmarks, Runnable refresh,
		Supplier<RecipeTreePreview> currentPreview) {
		var hovered = slotAt(area, x, y);
		if (hovered.isEmpty()) {
			return false;
		}
		var saved = selection.source(hovered.get().slot());
		if (saved.isEmpty() || saved.get().metadata().permutations().size() <= 1) {
			return false;
		}
		int slotIndex = layout.getRecipeSlotsView().getSlotViews().indexOf(hovered.get().slot());
		if (slotIndex < 0) {
			return false;
		}
		var keys = selection.getCandidates(hovered.get().slot()).stream()
			.map(value -> BookmarkItemMetadataFactory.createPermutationKey(value, Internal.getJeiRuntime().getIngredientManager())).toList();
		var screen = Minecraft.getInstance().screen;
		var source = new IIngredientCandidateSource() {
			private long version = bookmarks.getChangeVersion();

			private IRecipeSlotView slot() { return currentPreview.get().layout.getRecipeSlotsView().getSlotViews().get(slotIndex); }
			@Override
			public Optional<ITypedIngredient<?>> getSelectedIngredient() {
				return slot().getDisplayedIngredient();
			}
			@Override
			public boolean isValid() {
				return Minecraft.getInstance().screen == screen && currentPreview.get() != null && bookmarks.getChangeVersion() == version && slotIndex < currentPreview.get().layout.getRecipeSlotsView().getSlotViews().size();
			}
			@Override
			public boolean canSelect() {
				return hovered.get().slot().getRole() == RecipeIngredientRole.INPUT;
			}
			@Override
			public boolean select(ITypedIngredient<?> ingredient, boolean synchronize) {
				if (!isValid() || !currentPreview.get().selection.select(slot(), ingredient, synchronize, bookmarks)) {
					return false;
				}
				refresh.run();
				version = bookmarks.getChangeVersion();
				return true;
			}
		};
		var tooltip = new JeiTooltip();
		source.addTooltip(tooltip);
		BookmarkCandidateTooltipHelper.addTo(tooltip, state, keys, () -> source);
		tooltip.draw(graphics, x, y);
		return true;
	}

	public int width() { return layout.getRectWithBorder().getWidth(); }
	public void tick() { layout.tick(); }

	public Map<Integer, BookmarkIngredientKey> selectedKeys() { return selection.selectedKeys(); }

	public Optional<RecipeChainInput> sourceAt(Area area, double x, double y) {
		return slotAt(area, x, y).flatMap(slot -> selection.source(slot.slot()));
	}

	public boolean scroll(Area area, double x, double y, double delta, boolean synchronize, BookmarkList bookmarks) {
		return area.contains(x, y) && selection.scroll(area.localX(x), area.localY(y), delta, synchronize, bookmarks);
	}

	public Area area(int x, int y, int availableWidth) {
		var border = layout.getRectWithBorder();
		double scale = Math.min(1, Math.max(1, availableWidth) / (double) Math.max(1, border.getWidth()));
		return new Area(x, y, border.getWidth() * scale, border.getHeight() * scale, scale);
	}

	public void draw(GuiGraphics graphics, Area area, int mouseX, int mouseY, boolean hovered) {
		graphics.pose().pushPose();
		try {
			graphics.pose().translate(area.x(), area.y(), 0);
			graphics.pose().scale((float) area.scale(), (float) area.scale(), 1);
			layout.drawRecipe(graphics, hovered ? area.localX(mouseX) : -10000, hovered ? area.localY(mouseY) : -10000);
			graphics.flush();
		} finally {
			graphics.pose().popPose();
		}
	}

	public Optional<RecipeSlotUnderMouse> slotAt(Area area, double x, double y) {
		return area.contains(x, y) ? layout.getSlotUnderMouse(area.localX(x), area.localY(y)) : Optional.empty();
	}

	public record Area(int x, int y, double width, double height, double scale) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + width && py >= y && py < y + height;
		}
		public int localX(double px) { return (int) Math.floor((px - x) / scale); }
		public int localY(double py) { return (int) Math.floor((py - y) / scale); }
	}
}

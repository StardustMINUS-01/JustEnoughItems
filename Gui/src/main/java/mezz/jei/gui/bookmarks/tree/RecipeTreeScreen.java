package mezz.jei.gui.bookmarks.tree;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.gui.input.handlers.UserInputRouter;
import mezz.jei.gui.recipes.RecipesGui;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.platform.Services;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkCandidateTooltipHelper;
import mezz.jei.gui.bookmarks.BookmarkCandidateTooltipState;
import mezz.jei.gui.bookmarks.RecipeLayoutProjection;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.overlay.bookmarks.BookmarkAmountFormatter;
import mezz.jei.gui.overlay.bookmarks.PlayerInventoryRecipeChainTooltipInventoryProvider;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.recipes.FocusedRecipeLayoutResolver;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;

public final class RecipeTreeScreen extends Screen {
	private static final int TOP = 34;
	private @Nullable Screen parent;
	private final BookmarkList bookmarks;
	private final int groupId;
	private final IIngredientManager ingredients;
	private final PlayerInventoryRecipeChainTooltipInventoryProvider inventoryProvider;
	private final @Nullable AbstractContainerMenu sourceMenu;
	private List<RecipeChainInput> inventoryInputs = List.of();
	private final Map<Integer, StackView<?>> stacks = new HashMap<>();
	private final Map<ResourceLocation, Machine> machines = new HashMap<>();
	private final List<SummaryEntry> summary = new ArrayList<>();
	private final List<SummaryEntry> recipeDetails = new ArrayList<>();
	private @Nullable RecipeTreeSidebarLayout<SummaryEntry> sidebarLayout;
	private final BookmarkCandidateTooltipState candidateTooltips = new BookmarkCandidateTooltipState();
	private final UserInputRouter candidateInputs;
	private @Nullable RecipeTreeLayout.Node selected;
	private @Nullable RecipeTreePreview preview;
	private @Nullable RecipeTreeBookmarkPanel bookmarkPanel;
	private @Nullable Button closeSidebar;
	private @Nullable Button refreshInventory;
	private @Nullable RecipeTreeLayout tree;
	private long version = -1;
	private double zoom = 1, panX, panY;
	private boolean dragging;
	private boolean showSummary;
	private boolean showBookmarks;
	private int sidebarScroll;
	private @Nullable Component notice;
	private String heading = "";
	private String searchText = "";
	private @Nullable EditBox searchBox;
	private boolean showDemand = true;
	private boolean remainingView;
	private @Nullable Button inventoryViewButton;
	private final Map<RecipeTreeLayout.Node, StackView<?>> demandStacks = new HashMap<>();
	private @Nullable RecipeTreeViewState pendingViewState;
	private int bookmarkRow;

	public RecipeTreeScreen(Screen parent, BookmarkList bookmarks, int groupId, IIngredientManager ingredients) {
		super(Component.translatable("jei.tree.title"));
		this.parent = parent;
		this.bookmarks = bookmarks;
		this.groupId = groupId;
		this.ingredients = ingredients;
		// This standalone screen has no overlay GUI properties, so global JEI input skips it.
		var recipesGui = (RecipesGui) Internal.getJeiRuntime().getRecipesGui();
		this.candidateInputs = new UserInputRouter("Recipe tree candidates", recipesGui.getForegroundInputLayer());
		bookmarks.getTreeViewState(groupId).ifPresent(state -> {
			pendingViewState = state;
			showBookmarks = state.bookmarksVisible();
			showSummary = state.summaryVisible();
			showDemand = state.demandVisible();
			remainingView = state.remainingView();
			searchText = state.search();
			sidebarScroll = state.sidebarScroll();
			bookmarkRow = state.bookmarkRow();
		});
		var client = Minecraft.getInstance();
		this.inventoryProvider = new PlayerInventoryRecipeChainTooltipInventoryProvider(client, ingredients);
		this.sourceMenu = client.player == null ? null : client.player.containerMenu;
	}

	@Override
	protected void init() {
		prepareBookmarkPanel();
		var back = addRenderableWidget(new Button(5, 6, 20, 20, Component.literal("<"), button -> onClose(), narration -> narration.get()) {
			private boolean pressed;

			@Override
			public void onClick(double x, double y) {
				// Keep the release on this screen so it cannot activate the parent's FTB sidebar.
				pressed = true;
			}

			@Override
			public void onRelease(double x, double y) {
				if (pressed) {
					pressed = false;
					if (clicked(x, y)) {
						onPress();
					}
				}
			}
		});
		back.setTooltip(Tooltip.create(Component.translatable("gui.back")));
		addTool("[]", "jei.tree.fit", 29, this::fit);
		addTool("-", "jei.tree.zoom_out", 53, () -> zoomAt(0.8, (viewportLeft() + viewportRight()) / 2.0, height / 2.0));
		addTool("+", "jei.tree.zoom_in", 77, () -> zoomAt(1.25, (viewportLeft() + viewportRight()) / 2.0, height / 2.0));
		addRenderableWidget(Button.builder(Component.literal(showBookmarks ? "<|" : "|>"), this::toggleBookmarks)
			.bounds(101, 6, 20, 20).tooltip(Tooltip.create(Component.translatable(showBookmarks ? "jei.tree.hide_bookmarks" : "jei.tree.show_bookmarks"))).build());
		addTool("=", "jei.tree.totals", 125, () -> { showSummary = !showSummary; sidebarScroll = 0; sidebarLayout = null; });
		addRenderableWidget(Button.builder(
				modeIcon("#", showDemand),
				button -> {
					showDemand = !showDemand;
					button.setMessage(modeIcon("#", showDemand));
					if (!showDemand) {
						remainingView = false;
					}
					inventoryViewButton.active = showDemand;
					inventoryViewButton.setMessage(modeIcon("I", remainingView));
					updateDemands();
				}
			)
			.bounds(149, 6, 20, 20).tooltip(Tooltip.create(Component.translatable("jei.tree.demand_view"))).build());
		inventoryViewButton = addRenderableWidget(Button.builder(
				modeIcon("I", remainingView),
				button -> {
					remainingView = !remainingView;
					button.setMessage(modeIcon("I", remainingView));
					updateDemands();
				}
			)
			.bounds(173, 6, 20, 20).tooltip(Tooltip.create(Component.translatable("jei.tree.remaining_view"))).build());
		inventoryViewButton.active = showDemand;
		int searchWidth = Math.min(150, Math.max(40, width - 208));
		searchBox = addRenderableWidget(new EditBox(font, width - searchWidth - 6, 7, searchWidth, 18, Component.translatable("jei.tree.search")));
		searchBox.setMaxLength(128);
		searchBox.setHint(Component.translatable("jei.tree.search"));
		searchBox.setValue(searchText);
		searchBox.setResponder(value -> { searchText = value; updateSearch(); });
		refreshInventory = addRenderableWidget(new Button(width - 48, TOP + 2, 20, 20, Component.literal("\u21bb"), button -> {
			updateInventory();
			refreshSummary(bookmarks.getRecipeChainTooltipInputs(groupId), bookmarks.getRecipeChainDetails(groupId));
		}, narration -> narration.get()) {
			@Override
			public void renderString(GuiGraphics graphics, Font font, int color) {
				graphics.pose().pushPose();
				graphics.pose().translate(getX() + getWidth() / 2.0f, getY() + (getHeight() - font.lineHeight * 2) / 2.0f, 0);
				graphics.pose().scale(2, 2, 1);
				graphics.drawCenteredString(font, getMessage(), 0, 0, color);
				graphics.pose().popPose();
			}
		});
		refreshInventory.setTooltip(Tooltip.create(Component.translatable("jei.tree.refresh_inventory")));
		refreshInventory.visible = sidebarOpen();
		closeSidebar = addRenderableWidget(Button.builder(
				Component.literal("x"),
				button -> {
					selected = null;
					preview = null;
					showSummary = false;
					recipeDetails.clear();
				}
			)
			.bounds(width - 24, TOP + 2, 20, 20).tooltip(Tooltip.create(Component.translatable("gui.close"))).build());
		closeSidebar.visible = sidebarOpen();
		if (version < 0) {
			updateInventory();
		}
		if (version != bookmarks.getChangeVersion()) {
			refresh();
			if (pendingViewState == null) {
				fit();
			}
		}
		if (pendingViewState != null) {
			zoom = pendingViewState.zoom();
			panX = (viewportLeft() + viewportRight()) / 2.0 - pendingViewState.centerX() * zoom;
			panY = (TOP + height) / 2.0 - pendingViewState.centerY() * zoom;
			pendingViewState = null;
		}
	}

	@Override
	public void resize(Minecraft minecraft, int width, int height) {
		double centerX = ((viewportLeft() + viewportRight()) / 2.0 - panX) / zoom;
		double centerY = ((TOP + this.height) / 2.0 - panY) / zoom;
		super.resize(minecraft, width, height);
		panX = (viewportLeft() + viewportRight()) / 2.0 - centerX * zoom;
		panY = (TOP + height) / 2.0 - centerY * zoom;
	}

	private void addTool(String icon, String tooltip, int x, Runnable action) {
		addRenderableWidget(Button.builder(Component.literal(icon), button -> action.run())
			.bounds(x, 6, 20, 20).tooltip(Tooltip.create(Component.translatable(tooltip))).build());
	}

	@Override
	public void tick() {
		if (version != bookmarks.getChangeVersion()) {
			refresh();
		}
		if (showBookmarks && bookmarkPanel != null) {
			bookmarkPanel.refresh();
		}
		if (!showSummary && preview != null) {
			preview.tick();
		}
	}

	private void updateInventory() {
		inventoryInputs = inventoryProvider.getTreeInventoryInputs(groupId, sourceMenu);
		if (remainingView) {
			updateDemands();
		}
	}

	private static Component modeIcon(String icon, boolean enabled) {
		return Component.literal(icon).withStyle(enabled ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
	}

	private void updateDemands() {
		demandStacks.clear();
		if (tree == null) {
			return;
		}
		tree.updateDemands(inventoryInputs, bookmarks.getCollapsedRecipeIds(groupId), remainingView);
		updateDemandStacks();
	}

	private void updateDemandStacks() {
		if (!showDemand) {
			return;
		}
		for (var node : tree.nodes()) {
			var source = node.target();
			var original = stacks.get(source.index());
			if (original != null) {
				demandStacks.computeIfAbsent(node, ignored -> original.withAmount(node.demandAmount()));
			}
		}
	}

	private @Nullable StackView<?> nodeStack(RecipeTreeLayout.Node node) {
		return showDemand ? demandStacks.get(node) : stacks.get(node.target().index());
	}

	private void updateSearch() {
		if (tree == null || searchText.isBlank()) {
			return;
		}
		String query = searchText.strip().toLowerCase(Locale.ROOT);
		tree.search(input -> {
			var stack = stacks.get(input.index());
			return stack != null && stack.searchName.contains(query);
		});
	}

	private void refreshSummary(List<RecipeChainInput> inputs, Optional<RecipeChainDetails> details) {
		summary.clear();
		var model = RecipeTreeSummary.create(inputs, details, bookmarks.getCollapsedRecipeIds(groupId), inventoryInputs, ingredients);
		for (var section : model.sections()) {
			var label = Component.translatable(section.type().translationKey());
			if (section.items().isEmpty()) {
				label.append(" -");
			}
			summary.add(new SummaryEntry(label, null));
			for (var item : section.items()) {
				ITypedIngredient<?> ingredient = item.ingredient() != null ? item.ingredient() : item.key().typedIngredient();
				if (ingredient != null) {
					summary.add(new SummaryEntry(Component.empty(), stack(ingredient, item.amount(), false)));
				}
			}
		}
		if (showSummary) {
			sidebarLayout = null;
		}
	}

	private void refresh() {
		var expansion = tree != null ? tree.captureExpansion(selected) : pendingViewState == null ? null : pendingViewState.expansion();
		// The left panel can select a recipe outside the instantiated tree branches.
		var detachedRecipe = selected != null && expansion != null && expansion.selected() < 0 ? selected.target().metadata().recipeUid() : null;
		var previousChoices = preview == null ? Map.<Integer, BookmarkIngredientKey>of() : preview.selectedKeys();
		version = bookmarks.getChangeVersion();
		if (showBookmarks && bookmarkPanel != null) {
			bookmarkPanel.refresh();
		}
		String groupTitle = bookmarks.getBookmarkGroups().stream().filter(group -> group.id() == groupId).map(BookmarkGroup::title).findFirst().orElse("");
		heading = title.getString() + (groupTitle.isBlank() ? "" : " : " + groupTitle);
		stacks.clear();
		machines.clear();
		recipeDetails.clear();
		selected = null;
		preview = null;
		notice = null;
		var details = bookmarks.getRecipeChainDetails(groupId);
		List<RecipeChainInput> inputs = bookmarks.getRecipeChainTooltipInputs(groupId);
		tree = details.map(value -> new RecipeTreeLayout(inputs, value)).orElse(null);
		Optional<RecipeTreeLayout.Node> cachedSelection = Optional.empty();
		if (tree != null && expansion != null) {
			cachedSelection = tree.restoreExpansion(expansion);
		}
		for (RecipeChainInput input : inputs) {
			if (input.selectedIngredient() != null) {
				var view = stack(input.selectedIngredient(), Math.max(0, input.metadata().factor()), input.metadata().type().isNonConsumable());
				view.source = input;
				stacks.put(input.index(), view);
			}
			ResourceLocation type = input.metadata().recipeTypeUid();
			if (type != null) {
				machines.computeIfAbsent(type, this::machine);
			}
		}
		refreshSummary(inputs, details);
		updateDemands();
		updateSearch();
		cachedSelection.or(() -> tree == null || detachedRecipe == null ? Optional.empty() : tree.recipeNode(detachedRecipe))
			.ifPresent(node -> refreshSelection(node, previousChoices));
		sidebarLayout = null;
	}

	public boolean addRecipe(IRecipeLayoutDrawable<?> layout, Map<Integer, BookmarkIngredientKey> choices, Map<Integer, List<ITypedIngredient<?>>> candidates) {
		return bookmarks.addRecipeToGroup(groupId, new RecipeLayoutProjection(layout, choices, candidates));
	}

	private Machine machine(ResourceLocation type) {
		var manager = Internal.getJeiRuntime().getRecipeManager();
		return manager.getRecipeType(type).map(manager::getRecipeCategory)
			.map(category -> new Machine(category.getTitle(), category.getIcon()))
			.orElseGet(() -> new Machine(Component.literal(type.toString()), null));
	}

	private boolean sidebarOpen() { return showSummary || selected != null; }
	private int viewportLeft() { return showBookmarks ? Math.min(138, Math.max(48, width / 4)) : 0; }

	private void prepareBookmarkPanel() {
		if (!showBookmarks) {
			return;
		}
		if (bookmarkPanel == null) {
			bookmarkPanel = new RecipeTreeBookmarkPanel(bookmarks, groupId, ingredients);
			bookmarkPanel.updateBounds(viewportLeft(), TOP, height);
			bookmarkPanel.restoreFirstRow(bookmarkRow);
			return;
		}
		bookmarkPanel.updateBounds(viewportLeft(), TOP, height);
	}

	private void toggleBookmarks(Button button) {
		int previousLeft = viewportLeft();
		showBookmarks = !showBookmarks;
		panX += (viewportLeft() - previousLeft) / 2.0;
		if (bookmarkPanel != null) {
			bookmarkPanel.release();
		}
		prepareBookmarkPanel();
		button.setMessage(Component.literal(showBookmarks ? "<|" : "|>"));
		button.setTooltip(Tooltip.create(Component.translatable(showBookmarks ? "jei.tree.hide_bookmarks" : "jei.tree.show_bookmarks")));
	}
	private int viewportRight() {
		int desiredWidth = !showSummary && preview != null ? Math.max(180, preview.width() + 12) : 180;
		return sidebarOpen() ? Math.max(100, width - Math.min(desiredWidth, width / 2)) : width;
	}

	private @Nullable RecipeTreePreview.Area previewArea() {
		return !showSummary && preview != null ? preview.area(viewportRight() + 6, TOP + 26 - sidebarScroll, width - viewportRight() - 12) : null;
	}

	private int previewHeight() {
		var area = previewArea();
		return area == null ? 0 : (int) Math.ceil(area.height()) + 8;
	}

	private boolean sidebarContentAt(double x, double y) {
		return sidebarOpen() && x >= viewportRight() && x < width && y >= TOP + 24 && y < height;
	}

	private Optional<RecipeSlotUnderMouse> previewSlotAt(double x, double y) {
		var area = previewArea();
		return area != null && sidebarContentAt(x, y) ? preview.slotAt(area, x, y) : Optional.empty();
	}

	private Optional<RecipeChainInput> previewSourceAt(double x, double y) {
		var area = previewArea();
		return area != null && sidebarContentAt(x, y) ? preview.sourceAt(area, x, y) : Optional.empty();
	}

	private @Nullable SummaryEntry sidebarEntryAt(double x, double y) {
		if (!sidebarContentAt(x, y)) {
			return null;
		}
		return sidebarLayout().entryAt(x - viewportRight() - 6, y - (TOP + 26 + previewHeight() - sidebarScroll)).orElse(null);
	}

	private RecipeTreeSidebarLayout<SummaryEntry> sidebarLayout() {
		int availableWidth = Math.max(1, width - viewportRight() - 12);
		if (sidebarLayout == null || sidebarLayout.width() != availableWidth) {
			sidebarLayout = new RecipeTreeSidebarLayout<>(showSummary ? summary : recipeDetails, availableWidth, entry -> entry.stack() == null);
		}
		return sidebarLayout;
	}

	private int sidebarMaxScroll() {
		return Math.max(0, previewHeight() + sidebarLayout().height() - (height - TOP - 26));
	}

	private void fit() {
		if (tree == null) {
			return;
		}
		zoom = Math.clamp(Math.min((viewportRight() - viewportLeft() - 24) / Math.max(1, tree.width()), (height - TOP - 24) / Math.max(1, tree.height())), 0.0001, 1.5);
		panX = viewportLeft() + (viewportRight() - viewportLeft() - tree.width() * zoom) / 2;
		panY = TOP + 12;
	}

	private void zoomAt(double factor, double x, double y) {
		double next = Math.clamp(zoom * factor, 0.0001, 3);
		panX = x - (x - panX) * next / zoom;
		panY = y - (y - panY) * next / zoom;
		zoom = next;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Draw the dim background before the tree, without vanilla's blur pass.
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xB3000000);
		graphics.enableScissor(viewportLeft(), TOP, viewportRight(), height);
		graphics.pose().pushPose();
		graphics.pose().translate(panX, panY, 0);
		graphics.pose().scale((float) zoom, (float) zoom, 1);
		if (tree != null) {
			for (var node : tree.nodes()) {
				drawConnection(graphics, node);
			}
			for (var node : tree.nodes()) {
				if (visible(node)) {
					drawNode(graphics, node);
				}
			}
		}
		graphics.flush();
		graphics.pose().popPose();
		graphics.disableScissor();
		if (tree == null || tree.nodes().isEmpty()) {
			graphics.drawCenteredString(font, Component.translatable("jei.tree.empty"), (viewportLeft() + viewportRight()) / 2, height / 2, 0xFFCCCCCC);
		}
		graphics.fill(0, 0, width, TOP, 0x66000000);
		graphics.drawString(font, font.plainSubstrByWidth(heading, Math.max(0, (searchBox == null ? width : searchBox.getX()) - 208)), 202, 12, 0xFFE5E9E5);
		if (sidebarOpen()) {
			drawSidebar(graphics, mouseX, mouseY);
		}
		if (showBookmarks && bookmarkPanel != null) {
			bookmarkPanel.draw(graphics, viewportLeft(), TOP, height, mouseX, mouseY);
		}
		if (closeSidebar != null) {
			closeSidebar.visible = sidebarOpen();
		}
		if (refreshInventory != null) {
			refreshInventory.visible = sidebarOpen();
		}
		super.render(graphics, mouseX, mouseY, partialTick);
		if (notice != null) {
			graphics.drawCenteredString(font, notice, viewportRight() / 2, height - 12, 0xFFFFD36A);
		}
		var previewSlot = previewSlotAt(mouseX, mouseY);
		if (mouseX < viewportLeft() && mouseY >= TOP && bookmarkPanel != null) {
			bookmarkPanel.drawTooltip(graphics, mouseX, mouseY);
			return;
		}
		SummaryEntry sidebarHovered = sidebarEntryAt(mouseX, mouseY);
		if (previewSlot.isPresent()) {
			if (!preview.showCandidates(graphics, previewArea(), mouseX, mouseY, candidateTooltips, bookmarks, this::refresh, () -> preview)) {
				previewSourceAt(mouseX, mouseY).map(source -> stacks.get(source.index())).ifPresent(stack -> stack.tooltip(graphics, mouseX, mouseY));
			}
		} else if (sidebarHovered != null) {
			if (sidebarHovered.stack() != null) {
				sidebarHovered.stack().tooltip(graphics, mouseX, mouseY);
			} else {
				graphics.renderTooltip(font, sidebarHovered.title(), mouseX, mouseY);
			}
		} else if (mouseY >= TOP && mouseX < viewportRight()) {
			drawHoveredTooltip(graphics, mouseX, mouseY);
		}
	}

	private boolean visible(RecipeTreeLayout.Node node) {
		return node.x() * zoom + panX < viewportRight() && (node.x() + RecipeTreeLayout.WIDTH) * zoom + panX > viewportLeft() &&
			node.y() * zoom + panY < height && (node.y() + node.height()) * zoom + panY > TOP;
	}

	private void drawConnection(GuiGraphics graphics, RecipeTreeLayout.Node node) {
		var parent = node.parent();
		if (parent == null) {
			return;
		}
		int x1 = (int) parent.x() + RecipeTreeLayout.WIDTH / 2;
		int y1 = (int) parent.y() + parent.height();
		int x2 = (int) node.x() + RecipeTreeLayout.WIDTH / 2;
		int y2 = (int) node.y();
		if (Math.min(x1, x2) * zoom + panX >= viewportRight() || Math.max(x1, x2) * zoom + panX < viewportLeft() ||
			y1 * zoom + panY >= height || y2 * zoom + panY < TOP
		) {
			return;
		}
		int middle = (y1 + y2) / 2;
		graphics.vLine(x1, y1, middle, 0xFF78998B);
		graphics.hLine(x1, x2, middle, 0xFF78998B);
		graphics.vLine(x2, middle, y2, 0xFF78998B);
	}

	private void drawNode(GuiGraphics graphics, RecipeTreeLayout.Node node) {
		int x = (int) node.x(), y = (int) node.y();
		graphics.fill(x, y, x + RecipeTreeLayout.WIDTH, y + node.height(), node.recipe() ? 0x66000000 : 0xFF323435);
		graphics.renderOutline(x, y, RecipeTreeLayout.WIDTH, node.height(), node == selected ? 0xFFE4C86C : node.recipe() ? 0xFF719786 : 0xFF777C7A);
		Machine machine = node.recipe() ? machines.get(node.target().metadata().recipeTypeUid()) : null;
		if (machine != null && machine.icon() != null) {
			graphics.pose().pushPose();
			graphics.pose().translate(x + 2, y + 2, 0);
			float size = 8f / Math.max(1, Math.max(machine.icon().getWidth(), machine.icon().getHeight()));
			graphics.pose().scale(size, size, 1);
			machine.icon().draw(graphics);
			graphics.pose().popPose();
		}
		StackView<?> stack = nodeStack(node);
		if (stack != null) {
			stack.draw(graphics, x + 8, y + 11);
		} else {
			graphics.drawString(font, "?", x + 12, y + 13, 0xFFAAAAAA);
		}
		if (node.expandable()) {
			graphics.drawString(font, node.expanded() ? "-" : "+", x + RecipeTreeLayout.WIDTH - 8, y + 1, 0xFFE4C86C, false);
		}
		if (!searchText.isBlank() && !tree.matchesSearch(node)) {
			graphics.pose().pushPose();
			graphics.pose().translate(0, 0, 400);
			graphics.fill(x, y, x + RecipeTreeLayout.WIDTH, y + node.height(), 0xCE333333);
			graphics.pose().popPose();
		}
	}

	private Optional<RecipeTreeLayout.Node> nodeAt(double x, double y) {
		if (tree == null || y < TOP || x < viewportLeft() || x >= viewportRight()) {
			return Optional.empty();
		}
		return tree.nodeAt((x - panX) / zoom, (y - panY) / zoom);
	}

	private void drawHoveredTooltip(GuiGraphics graphics, int x, int y) {
		nodeAt(x, y).ifPresent(node -> {
			double localX = (x - panX) / zoom - node.x(), localY = (y - panY) / zoom - node.y();
			if (localX >= RecipeTreeLayout.WIDTH - 10 && localY < 11 && node.expandable()) {
				graphics.renderTooltip(font, Component.translatable(node.expanded() ? "jei.tree.collapse" : "jei.tree.expand"), x, y);
			} else if (localX < 11 && localY < 11 && node.recipe()) {
				Machine machine = machines.get(node.target().metadata().recipeTypeUid());
				if (machine != null) {
					graphics.renderTooltip(font, machine.title(), x, y);
				}
			} else {
				StackView<?> stack = nodeStack(node);
				if (stack != null) {
					stack.tooltip(graphics, x, y);
				}
			}
		});
	}

	private void select(RecipeTreeLayout.Node node) {
		refreshSelection(node, Map.of());
		showSummary = false;
		sidebarLayout = null;
		sidebarScroll = 0;
	}

	private void refreshSelection(RecipeTreeLayout.Node node, Map<Integer, BookmarkIngredientKey> previousChoices) {
		if (selected != node) {
			preview = null;
			var metadata = node.target().metadata();
			if (node.recipe() && metadata.recipeTypeUid() != null && metadata.recipeUid() != null) {
				var runtime = Internal.getJeiRuntime();
				preview = new FocusedRecipeLayoutResolver(runtime.getRecipeManager())
					.resolve(new FocusedRecipe(metadata.recipeTypeUid(), metadata.recipeUid()), runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
					.map(layout -> new RecipeTreePreview(layout, node.slots(), ingredients, previousChoices)).orElse(null);
			}
		}
		selected = node;
		recipeDetails.clear();
		Machine machine = node.recipe() ? machines.get(node.target().metadata().recipeTypeUid()) : null;
		if (machine != null) {
			recipeDetails.add(new SummaryEntry(machine.title(), null, machine.icon()));
		}
		String previousSection = "";
		for (RecipeChainInput input : node.slots()) {
			String section = input.metadata().type().isGraphOutput() ? "jei.tooltip.bookmarks.group.recipe_chain.output" : input.metadata().type().isNonConsumable() ? "jei.tree.non_consumable" : "jei.tooltip.bookmarks.group.recipe_chain.input";
			if (!section.equals(previousSection)) {
				recipeDetails.add(new SummaryEntry(Component.translatable(section), null));
				previousSection = section;
			}
			StackView<?> stack = stacks.get(input.index());
			recipeDetails.add(new SummaryEntry(Component.literal("?"), stack));
		}
	}

	private void drawSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
		sidebarScroll = Math.clamp(sidebarScroll, 0, sidebarMaxScroll());
		int left = viewportRight();
		graphics.fill(left, TOP, width, height, 0x66000000);
		graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(showSummary ? "jei.tree.totals" : "jei.tree.recipe_details").getString(), Math.max(0, width - left - 56)), left + 6, TOP + 6, 0xFFE4C86C);
		graphics.enableScissor(left, TOP + 24, width, height);
		var area = previewArea();
		if (area != null && area.y() + area.height() > TOP + 24 && area.y() < height) {
			preview.draw(graphics, area, mouseX, mouseY, sidebarContentAt(mouseX, mouseY) && area.contains(mouseX, mouseY));
		}
		int contentTop = TOP + 26 + previewHeight() - sidebarScroll;
		for (var cell : sidebarLayout().cells()) {
			int x = left + 6 + cell.x();
			int y = contentTop + cell.y();
			if (y + 22 < TOP + 24 || y >= height) {
				continue;
			}
			SummaryEntry entry = cell.entry();
			if (entry.stack() == null) {
				int textX = left + 6;
				if (entry.icon() != null) {
					graphics.pose().pushPose();
					graphics.pose().translate(left + 8, y + 2, 0);
					float size = 16f / Math.max(1, Math.max(entry.icon().getWidth(), entry.icon().getHeight()));
					graphics.pose().scale(size, size, 1);
					entry.icon().draw(graphics);
					graphics.pose().popPose();
					textX = left + 34;
				}
				graphics.drawString(font, font.plainSubstrByWidth(entry.title().getString(), Math.max(0, width - textX - 6)), textX, y + 3, 0xFFADCEBD);
			} else {
				if (!showSummary && selected != null && entry.stack() == stacks.get(selected.target().index())) {
					graphics.renderOutline(x, y, 20, 20, 0xFFE4C86C);
				}
				entry.stack().draw(graphics, x + 2, y + 2);
			}
		}
		graphics.disableScissor();
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if (handleCandidateClick(x, y, button, InputType.SIMULATE)) {
			return true;
		}
		if (searchBox != null && !searchBox.isMouseOver(x, y)) {
			searchBox.setFocused(false);
		}
		if (super.mouseClicked(x, y, button)) {
			return true;
		}
		if (x < viewportLeft() && y >= TOP && bookmarkPanel != null) {
			if (button == 0 && bookmarkPanel.clickScrollbar(x, y, viewportLeft())) {
				return true;
			}
			if (button == 0 && tree != null) {
				bookmarkPanel.bookmarkAt(x, y).ifPresent(bookmark -> {
					var metadata = bookmarks.getBookmarkMetadata(bookmark);
					if (metadata.recipeUid() != null) {
						tree.recipeNode(metadata.recipeUid()).ifPresent(this::select);
					}
				});
			}
			return true;
		}
		if (button != 0 || y < TOP || x >= viewportRight()) {
			return false;
		}
		var node = nodeAt(x, y);
		if (node.isPresent()) {
			var action = RecipeTreeInput.click(button, hasShiftDown()).orElseThrow();
			if (action == RecipeTreeInput.SELECT) {
				select(node.get());
				panX = Math.min(panX, viewportRight() - 8 - (node.get().x() + RecipeTreeLayout.WIDTH) * zoom);
				return true;
			}
			double oldX = node.get().x(), oldY = node.get().y();
			int allocated = tree.getAllocatedNodeCount();
			boolean changed = !node.get().expandable() || tree.toggle(node.get());
			if (changed && showDemand) {
				if (allocated != tree.getAllocatedNodeCount()) {
					updateDemands();
				} else {
					updateDemandStacks();
				}
			}
			panX += (oldX - node.get().x()) * zoom;
			panY += (oldY - node.get().y()) * zoom;
			notice = changed ? null : Component.translatable("jei.tree.limit");
		} else {
			dragging = true;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		if (candidateInputs.handleMouseDragged(x, y, InputConstants.Type.MOUSE.getOrCreate(button), dx, dy)) {
			return true;
		}
		if (button == 0 && showBookmarks && bookmarkPanel != null && bookmarkPanel.dragScrollbar(y)) {
			return true;
		}
		if (button == 0 && dragging) {
			panX += dx;
			panY += dy;
			return true;
		}
		return super.mouseDragged(x, y, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		if (handleCandidateClick(x, y, button, InputType.EXECUTE)) {
			return true;
		}
		if (bookmarkPanel != null) {
			bookmarkPanel.release();
		}
		dragging = false;
		return super.mouseReleased(x, y, button);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (candidateInputs.handleMouseScrolled(x, y, horizontal, vertical)) {
			return true;
		}
		if (y < TOP) {
			return false;
		}
		if (x < viewportLeft() && bookmarkPanel != null) {
			bookmarkPanel.scroll(x, y, vertical, hasControlDown(), hasAltDown(), hasShiftDown());
			if (version != bookmarks.getChangeVersion()) {
				refresh();
			}
			return true;
		}
		var area = previewArea();
		if ((hasShiftDown() || hasControlDown()) && area != null && sidebarContentAt(x, y) && area.contains(x, y)) {
			preview.scroll(area, x, y, vertical, hasShiftDown(), bookmarks);
			if (version != bookmarks.getChangeVersion()) {
				refresh();
			}
			return true;
		}
		if (x >= viewportRight() && sidebarOpen()) {
			sidebarScroll = Math.clamp(sidebarScroll - (int) (vertical * 24), 0, sidebarMaxScroll());
		} else {
			zoomAt(Math.pow(1.15, vertical), x, y);
		}
		return true;
	}

	@Override
	public boolean isPauseScreen() { return false; }

	private boolean handleCandidateClick(double x, double y, int button, InputType type) {
		return UserInput.fromVanilla(x, y, button, type)
			.map(input -> candidateInputs.handleUserInput(this, input, Internal.getKeyMappings()))
			.orElse(false);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchBox != null && searchBox.isFocused()) {
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
		UserInput input = UserInput.fromVanilla(keyCode, scanCode, modifiers, InputType.IMMEDIATE);
		var action = RecipeTreeInput.key(input, Internal.getKeyMappings());
		if (action.isEmpty()) {
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
		var ingredient = hoveredIngredient(input.getMouseX(), input.getMouseY());
		if (ingredient.isEmpty()) {
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
		if (action.get() == RecipeTreeInput.BOOKMARK) {
			var element = !showBookmarks || bookmarkPanel == null ? Optional.<IElement<?>>empty() : bookmarkPanel.elementAt(input.getMouseX(), input.getMouseY());
			if (element.isPresent()) {
				var bookmark = element.get().getBookmark().orElseThrow();
				bookmarks.removeExpandedRecipeBookmark(bookmark);
				refresh();
			} else {
				bookmarks.addIngredientBookmark(ingredient.get());
			}
		} else {
			var runtime = Internal.getJeiRuntime();
			var recipesGui = runtime.getRecipesGui();
			Screen previousParent = recipesGui.getParentScreen().orElse(null);
			var roles = action.get() == RecipeTreeInput.SHOW_RECIPE ? List.of(RecipeIngredientRole.OUTPUT) : List.of(RecipeIngredientRole.INPUT, RecipeIngredientRole.CATALYST);
			var focusUtil = new FocusUtil(runtime.getJeiHelpers().getFocusFactory(), Internal.getJeiClientConfigs().getClientConfig(), ingredients);
			recipesGui.show(focusUtil.createFocuses(ingredient.get(), roles));
			// RecipesGui is a singleton: do not make it both our parent and our child after an R/U lookup.
			if (minecraft.screen == recipesGui && parent == recipesGui) {
				parent = previousParent;
			}
		}
		return true;
	}

	private Optional<ITypedIngredient<?>> hoveredIngredient(double x, double y) {
		if (x < viewportLeft() && bookmarkPanel != null) {
			return bookmarkPanel.elementAt(x, y).map(element -> element.getTypedIngredient());
		}
		var slot = previewSlotAt(x, y);
		if (slot.isPresent()) {
			return slot.get().slot().getDisplayedIngredient();
		}
		var entry = sidebarEntryAt(x, y);
		if (entry != null && entry.stack() != null) {
			return Optional.of(entry.stack().typed);
		}
		return nodeAt(x, y).map(node -> stacks.get(node.target().index())).map(stack -> stack.typed);
	}

	@Override
	public void onClose() { minecraft.setScreen(parent); }

	@Override
	public void removed() {
		candidateInputs.handleGuiChange();
		cacheViewState();
		super.removed();
	}

	private void cacheViewState() {
		if (tree == null) {
			return;
		}
		var expansion = tree.captureExpansion(selected);
		bookmarks.cacheTreeViewState(groupId, new RecipeTreeViewState(zoom,
			((viewportLeft() + viewportRight()) / 2.0 - panX) / zoom, ((TOP + height) / 2.0 - panY) / zoom,
			showBookmarks, showSummary || selected != null && expansion.selected() < 0, showDemand, remainingView,
			searchText, sidebarScroll, bookmarkPanel == null ? bookmarkRow : bookmarkPanel.firstRow(), expansion));
	}

	private <T> StackView<T> stack(ITypedIngredient<T> ingredient, long amount, boolean nonConsumable) {
		return new StackView<>(ingredient, ingredients.getIngredientRenderer(ingredient.getType()), amount, nonConsumable);
	}

	private record Machine(Component title, @Nullable IDrawable icon) {}
	private record SummaryEntry(Component title, @Nullable StackView<?> stack, @Nullable IDrawable icon) {
		private SummaryEntry(Component title, @Nullable StackView<?> stack) { this(title, stack, null); }
	}

	private final class StackView<T> {
		private final ITypedIngredient<T> typed;
		private final IIngredientRenderer<T> renderer;
		private final T icon;
		private final T tooltipIngredient;
		private final boolean nonConsumable;
		private final String amountText;
		private final String searchName;
		private @Nullable RecipeChainInput source;

		private StackView(ITypedIngredient<T> typed, IIngredientRenderer<T> renderer, long amount, boolean nonConsumable) {
			this.typed = typed;
			this.renderer = renderer;
			this.nonConsumable = nonConsumable;
			var helper = ingredients.getIngredientHelper(typed.getType());
			searchName = helper.getDisplayName(typed.getIngredient()).toLowerCase(Locale.ROOT);
			var fluidHelper = Services.PLATFORM.getFluidHelper();
			long iconAmount = typed.getType().equals(fluidHelper.getFluidIngredientType()) ? fluidHelper.bucketVolume() : 1;
			icon = helper.copyWithAmount(typed.getIngredient(), iconAmount);
			tooltipIngredient = helper.copyWithAmount(typed.getIngredient(), Math.max(1, amount));
			amountText = BookmarkAmountFormatter.formatTypedAmount(amount, typed.getType().getUid());
		}

		private StackView(StackView<T> original, long amount) {
			typed = original.typed;
			renderer = original.renderer;
			icon = original.icon;
			nonConsumable = original.nonConsumable;
			searchName = original.searchName;
			source = original.source;
			tooltipIngredient = ingredients.getIngredientHelper(typed.getType()).copyWithAmount(typed.getIngredient(), Math.max(1, amount));
			amountText = BookmarkAmountFormatter.formatTypedAmount(amount, typed.getType().getUid());
		}

		private StackView<T> withAmount(long amount) { return new StackView<>(this, amount); }

		private void draw(GuiGraphics graphics, int x, int y) {
			graphics.pose().pushPose();
			graphics.pose().translate(x, y, 0);
			float size = 16f / Math.max(1, Math.max(renderer.getWidth(), renderer.getHeight()));
			graphics.pose().scale(size, size, 1);
			renderer.render(graphics, icon);
			graphics.pose().popPose();
			graphics.pose().pushPose();
			graphics.pose().translate(x + 16, y + 12, 350);
			float textScale = Math.min(0.65f, 23f / Math.max(1, font.width(amountText)));
			graphics.pose().scale(textScale, textScale, 1);
			graphics.drawString(font, amountText, -font.width(amountText), 0, 0xFFFFFFFF);
			graphics.pose().popPose();
			if (nonConsumable) {
				graphics.pose().pushPose();
				graphics.pose().translate(0, 0, 350);
				graphics.drawString(font, "C", x + 12, y - 2, 0xFFFFDD44);
				graphics.pose().popPose();
			}
		}

		private void tooltip(GuiGraphics graphics, int x, int y) {
			JeiTooltip tooltip = new JeiTooltip();
			tooltip.setIngredient(typed);
			renderer.getTooltip(tooltip, tooltipIngredient, minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL);
			tooltip.add(Component.translatable("jei.tree.amount", amountText).withStyle(ChatFormatting.GRAY));
			if (nonConsumable) {
				tooltip.add(Component.translatable("jei.tree.non_consumable").withStyle(ChatFormatting.YELLOW));
			}
			if (source != null && source.selectedKey() != null) {
				BookmarkCandidateTooltipHelper.addTo(tooltip, candidateTooltips, List.copyOf(source.metadata().permutations()),
					() -> bookmarks.getCandidateSource(bookmarks.getBookmarks().get(source.index())));
			}
			tooltip.draw(graphics, x, y);
		}
	}
}

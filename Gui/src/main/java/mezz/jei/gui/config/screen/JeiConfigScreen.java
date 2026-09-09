package mezz.jei.gui.config.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import mezz.jei.api.runtime.config.IJeiConfigManager;
import mezz.jei.api.runtime.config.IJeiConfigValue;
import mezz.jei.api.runtime.config.IJeiConfigListValueSerializer;
import mezz.jei.common.gui.elements.Scrollbar;
import mezz.jei.common.util.ImmutableRect2i;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public final class JeiConfigScreen extends Screen {
	private static final int ROW_HEIGHT = 30;
	private static final int SCROLLBAR_WIDTH = 6;
	private record Section(Component name, String file, List<ConfigDraft<?>> entries) {}
	private final Screen parent;
	private final List<IConfigKeyBinding> keyBindings;
	private List<IConfigKeyBinding> visibleKeys = List.of();
	private List<Component> keyTooltips = List.of();
	private long changedKeys;
	private final List<String> keyCategories;
	private boolean keyView;
	private boolean configExpanded = true, keysExpanded;
	private @Nullable String keyCategory;
	private @Nullable IConfigKeyBinding recording;
	private @Nullable InputConstants.Key recordedKey;
	private int recordedModifiers;
	private record Navigation(Component name, boolean selected, Runnable action) {}
	private List<Navigation> navigation = List.of();
	private final List<Section> sections = new ArrayList<>();
	private int section;
	private int row;
	private int navigationRow;
	private String search = "";
	private @Nullable ConfigListDraft<?> choosing;
	private int parentRow;
	private int listChoiceCount;
	private EditBox searchBox;
	private List<ConfigDraft<?>> visible = List.of();
	private String error = "";
	private int sidebar;
	private int capacity;
	private int navigationCapacity;
	private Scrollbar scrollbar;
	private Scrollbar navigationScrollbar;
	private record RenderedEntry(ConfigDraft<?> draft, List<FormattedCharSequence> tooltip) {}
	private final List<RenderedEntry> rendered = new ArrayList<>();
	private long changed;
	private final Map<ConfigDraft<?>, ConfigColorListDraft> colorDrafts = new HashMap<>();
	private @Nullable ConfigColorListDraft colorPage;

	public JeiConfigScreen(Screen parent, IJeiConfigManager manager, List<IConfigKeyBinding> keyBindings) {
		super(Component.translatable("jei.settings.title"));
		this.parent = parent;
		this.keyBindings = List.copyOf(keyBindings);
		this.keyCategories = keyBindings.stream().map(IConfigKeyBinding::getCategory).distinct().toList();
		manager.getConfigFiles().stream().sorted(Comparator.comparing(f -> f.getPath().toString())).forEach(file -> {
			String fileName = file.getPath().getFileName().toString();
			for (var category : file.getCategories()) {
				String key = "jei.config.client." + category.getName();
				Component label = Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(category.getName());
				List<ConfigDraft<?>> entries = new ArrayList<>();
				for (IJeiConfigValue<?> value : category.getConfigValues()) {
					ConfigDraft<?> draft = new ConfigDraft<>(value);
					entries.add(draft);
					draft.onChange = difference -> changed += difference;
					if (category.getName().equals("colors") && value.getName().equals("searchColors") &&
						value.getSerializer() instanceof IJeiConfigListValueSerializer<?> serializer) {
						colorDrafts.put(draft, new ConfigColorListDraft(draft, serializer));
						label = Component.translatable("jei.settings.colors");
					}
				}
				if (!entries.isEmpty()) {
					sections.add(new Section(label, fileName, entries));
				}
			}
		});
	}

	@Override
	protected void init() {
		sidebar = Math.min(158, width / 3);
		capacity = Math.max(1, (height - 117) / ROW_HEIGHT);
		navigationCapacity = Math.max(1, (height - 116) / 30);
		scrollbar = new Scrollbar(new ImmutableRect2i(width - SCROLLBAR_WIDTH - 1, 77, SCROLLBAR_WIDTH, capacity * ROW_HEIGHT));
		navigationScrollbar = new Scrollbar(new ImmutableRect2i(sidebar - SCROLLBAR_WIDTH - 1, 77, SCROLLBAR_WIDTH, navigationCapacity * 30));
		searchBox = new ConfigScreenEditBox(font, 9, 45, sidebar - 18, Component.translatable("jei.settings.search"));
		searchBox.setMaxLength(256);
		searchBox.setValue(search);
		searchBox.setHint(Component.translatable("jei.settings.search"));
		searchBox.setResponder(value -> {
			search = value;
			row = 0;
			choosing = null;
			updateVisible();
			rebuild();
			setFocused(searchBox);
		});
		updateVisible();
		rebuild();
	}

	private void rebuild() {
		changedKeys = keyBindings.stream().filter(IConfigKeyBinding::isChanged).count();
		error = "";
		clearWidgets();
		rendered.clear();
		colorPage = null;
		addRenderableWidget(searchBox);
		buildNavigation();
		navigationRow = Math.clamp(navigationRow, 0, Math.max(0, navigation.size() - navigationCapacity));
		for (int i = navigationRow; i < Math.min(navigation.size(), navigationRow + navigationCapacity); i++) {
			Navigation entry = navigation.get(i);
			addButton(8, 77 + (i - navigationRow) * 30, sidebar - 18, entry.name(),
				entry.selected() ? "tab_selected" : "tab", null, entry.action());
		}
		int right = width - 8;
		addButton(right - 56, height - 34, 56, Component.translatable("gui.ok"), "button_primary", null, this::save);
		addButton(right - 118, height - 34, 56, Component.translatable("gui.back"), "button", null, this::goBack);
		addButton(right - 180, height - 34, 56, Component.translatable("gui.cancel"), "button", null, () -> minecraft.setScreen(parent));
		if (keyView) {
			showKeys();
			return;
		}
		if (choosing != null) {
			showChoices(choosing);
			return;
		}

		if (search.isBlank() && visible.size() == 1) {
			colorPage = colorDrafts.get(visible.getFirst());
			if (colorPage != null) {
				showColors(colorPage);
				return;
			}
		}
		row = Math.clamp(row, 0, Math.max(0, visible.size() - capacity));
		for (int i = row; i < Math.min(visible.size(), row + capacity); i++) {
			ConfigDraft<?> draft = visible.get(i);
			addEditor(draft, 77 + (i - row) * ROW_HEIGHT);
		}
	}

	private void buildNavigation() {
		List<Navigation> entries = new ArrayList<>();
		entries.add(new Navigation(Component.literal(configExpanded ? "- " : "+ ").append(Component.translatable("jei.settings.configuration")),
			!keyView && section < 0, () -> {
				configExpanded = !configExpanded;
				showSection(-1);
			}));
		if (configExpanded) {
			for (int i = 0; i < sections.size(); i++) {
				int index = i;
				entries.add(new Navigation(Component.literal("  ").append(sections.get(i).name()), !keyView && section == i && search.isBlank(), () -> showSection(index)));
			}
		}
		entries.add(new Navigation(Component.literal(keysExpanded ? "- " : "+ ").append(Component.translatable("jei.settings.keybindings")),
			keyView && keyCategory == null, () -> {
				keysExpanded = !keysExpanded;
				showKeyCategory(null);
			}));
		if (keysExpanded) {
			for (String category : keyCategories) {
				entries.add(new Navigation(Component.literal("  ").append(Component.translatable(category)), keyView && category.equals(keyCategory) && search.isBlank(), () -> showKeyCategory(category)));
			}
		}
		navigation = entries;
	}

	private void showKeyCategory(@Nullable String category) {
		keyView = true;
		keyCategory = category;
		searchBox.setValue("");
	}

	private void showKeys() {
		row = Math.clamp(row, 0, Math.max(0, visibleKeys.size() - capacity));
		List<Component> tooltips = new ArrayList<>();
		for (int i = row; i < Math.min(visibleKeys.size(), row + capacity); i++) {
			IConfigKeyBinding binding = visibleKeys.get(i);
			int y = 77 + (i - row) * ROW_HEIGHT;
			String description = binding.getName() + ".description";
			Component tooltip = Component.translatable(Language.getInstance().has(description) ? description : binding.getName());
			var conflicts = binding.getConflicts();
			tooltips.add(tooltip);
			Component label = binding == recording ? Component.translatable("jei.settings.recordKey") : conflicts.isEmpty() ? binding.getBindingName() :
				Component.literal("[").append(binding.getBindingName()).append("]").withStyle(ChatFormatting.RED);
			addButton(width - 182, y, 126, label, binding == recording ? "button_primary" : "button", null, () -> {
				recording = binding;
				recordedKey = null;
				setFocused(null);
				rebuild();
			}, tooltip);
			addButton(width - 52, y, 40, Component.translatable("jei.settings.resetButton"), "button", null, () -> {
				binding.reset();
				rebuild();
			}, Component.translatable("jei.settings.reset")).active = !binding.isDefault();
		}
		keyTooltips = tooltips;
	}

	public boolean isRecordingKey() { return recording != null; }

	private static int currentModifiers() {
		return (hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0) | (hasControlDown() ?
			(net.minecraft.client.Minecraft.ON_OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL) : 0) | (hasAltDown() ? GLFW.GLFW_MOD_ALT : 0);
	}

	private void finishRecording() {
		if (recording.setKey(recordedKey, recordedModifiers)) {
			recording = null;
			recordedKey = null;
			rebuild();
		} else {
			recordedKey = null;
			error = Component.translatable("jei.settings.singleModifier").getString();
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (recording == null) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				save();
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			recording = null;
			recordedKey = null;
			rebuild();
		} else {
			recordedKey = InputConstants.getKey(keyCode, scanCode);
			recordedModifiers = modifiers;
		}
		return true;
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (recording == null) {
			return super.keyReleased(keyCode, scanCode, modifiers);
		}
		if (InputConstants.getKey(keyCode, scanCode).equals(recordedKey)) {
			if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
				recordedKey = InputConstants.UNKNOWN;
				recordedModifiers = 0;
			}
			finishRecording();
		}
		return true;
	}

	@Override
	public boolean charTyped(char character, int modifiers) {
		return recording != null || super.charTyped(character, modifiers);
	}

	private void updateVisible() {
		String query = search.toLowerCase(Locale.ROOT).strip();
		if (keyView) {
			visibleKeys = keyBindings.stream().filter(binding -> query.isEmpty() ? keyCategory == null || keyCategory.equals(binding.getCategory()) :
				(Component.translatable(binding.getName()).getString() + " " + binding.getName() + " " + binding.getBindingName().getString() + " " + Component.translatable(binding.getCategory()).getString()).toLowerCase(Locale.ROOT).contains(query)).toList();
			return;
		}
		visible = sections.isEmpty() ? List.of() : query.isEmpty() && section >= 0 ? sections.get(section).entries() : sections.stream()
			.flatMap(s -> s.entries().stream()).filter(draft -> (draft.config.getLocalizedName().getString() + " " +
				draft.config.getLocalizedDescription().getString() + " " + draft.config.getName()).toLowerCase(Locale.ROOT).contains(query)).toList();
	}

	private void showSection(int section) {
		this.section = section;
		keyView = false;
		searchBox.setValue("");
	}

	private ConfigScreenButton addButton(int x, int y, int width, Component name, String style, @Nullable String icon, Runnable action) {
		return addButton(x, y, width, name, style, icon, action, name);
	}

	private ConfigScreenButton addButton(int x, int y, int width, Component name, String style, @Nullable String icon, Runnable action, Component tooltip) {
		ConfigScreenButton button = addRenderableWidget(new ConfigScreenButton(x, y, width, name, style, icon, b -> action.run()));
		button.setTooltip(Tooltip.create(tooltip));
		return button;
	}

	private <T> void addEditor(ConfigDraft<T> draft, int y) {
		int controlWidth = Math.min(148, Math.max(70, (width - sidebar) / 2));
		int x = width - controlWidth - 12;
		controlWidth -= 44;
		ConfigScreenButton reset = addResetButton(draft, y, () -> {});
		Component tooltip = createTooltip(draft);
		rendered.add(new RenderedEntry(draft, font.split(tooltip, Math.min(300, width - 24))));
		var choices = draft.getChoices();
		var listSerializer = draft.getListSerializer();
		if (colorDrafts.containsKey(draft)) {
			addButton(x, y, controlWidth, Component.translatable("jei.settings.editColors"), "button", null, () -> {
				for (int i = 0; i < sections.size(); i++) {
					if (sections.get(i).entries().contains(draft)) {
						showSection(i);
						return;
					}
				}
			}, tooltip);
		} else if (draft.config.getDefaultValue() instanceof Boolean) {
			addButton(width - 88, y, 32, draft.config.getLocalizedName(),
				draft.getText().equals("true") ? "switch_on" : "switch_off", null, () -> {
					draft.setText(Boolean.toString(!Boolean.parseBoolean(draft.getText())));
					rebuild();
				}, tooltip);
		} else if (!choices.isEmpty()) {
			addButton(x, y, controlWidth, Component.literal(draft.getText()), "button", null, () -> {
				draft.cycle();
				rebuild();
			}, tooltip);
		} else if (listSerializer.isPresent()) {
			addButton(x, y, controlWidth, draft.getText().isBlank() ? Component.translatable("jei.settings.none") :
				Component.literal(draft.getText()), "button", null, () -> {
				choosing = new ConfigListDraft<>(draft, listSerializer.get());
				parentRow = row;
				row = 0;
				rebuild();
			}, tooltip);
		} else {
			EditBox edit = addRenderableWidget(new ConfigScreenEditBox(font, x, y + 2, controlWidth, draft.config.getLocalizedName()));
			edit.setMaxLength(32767);
			edit.setValue(draft.getText());
			edit.setTooltip(Tooltip.create(tooltip));
			edit.setResponder(value -> {
				draft.setText(value);
				reset.active = !draft.isDefault();
				edit.setTextColor(draft.getErrors().isEmpty() ? 0xFFE0E0E0 : 0xFFFF7777);
			});
			edit.setTextColor(draft.getErrors().isEmpty() ? 0xFFE0E0E0 : 0xFFFF7777);
		}
	}

	private <T> void showChoices(ConfigListDraft<T> draft) {
		List<T> selected = draft.getSelected();
		List<T> choices = draft.getChoices(selected);
		listChoiceCount = choices.size();
		row = Math.clamp(row, 0, Math.max(0, choices.size() - capacity));
		for (int i = row; i < Math.min(choices.size(), row + capacity); i++) {
			T choice = choices.get(i);
			int y = 77 + (i - row) * ROW_HEIGHT;
			int index = selected.indexOf(choice);
			String text = (index < 0 ? "" : (index + 1) + ". ") + draft.describe(choice);
			addButton(sidebar + 12, y, width - sidebar - 88, Component.literal(text),
				index >= 0 ? "button_primary" : "button", null, () -> {
					draft.toggle(choice);
					rebuild();
				});
			addButton(width - 68, y, 26, Component.translatable("jei.settings.moveUp"), "button", "icon_up", () -> {
				draft.move(index, -1);
				rebuild();
			}).active = index > 0;
			addButton(width - 38, y, 26, Component.translatable("jei.settings.moveDown"), "button", "icon_down", () -> {
				draft.move(index, 1);
				rebuild();
			}).active = index >= 0 && index < selected.size() - 1;
		}
	}

	private void goBack() {
		if (choosing != null) {
			choosing = null;
			row = parentRow;
			rebuild();
		} else if (!search.isBlank()) {
			searchBox.setValue("");
		} else if (keyView && keyCategory != null) {
			showKeyCategory(null);
		} else {
			minecraft.setScreen(parent);
		}
	}

	private void resetColorDraft(ConfigDraft<?> draft) {
		if (colorDrafts.containsKey(draft) && draft.config.getSerializer() instanceof IJeiConfigListValueSerializer<?> serializer) {
			colorDrafts.put(draft, new ConfigColorListDraft(draft, serializer));
		}
	}

	private ConfigScreenButton addResetButton(ConfigDraft<?> draft, int y, Runnable afterReset) {
		ConfigScreenButton button = addButton(width - 52, y, 40, Component.translatable("jei.settings.resetButton"), "button", null, () -> {
			draft.reset();
			resetColorDraft(draft);
			afterReset.run();
			rebuild();
		}, Component.translatable("jei.settings.reset"));
		button.active = !draft.isDefault();
		return button;
	}

	private void showColors(ConfigColorListDraft colors) {
		ConfigScreenButton reset = addResetButton(colors.draft, 43, () -> row = 0);
		addButton(width - 98, 43, 40, Component.translatable("jei.settings.addColor"), "button", null, () -> {
			colors.add();
			row = Math.max(0, colors.entries.size() - capacity);
			rebuild();
		});
		row = Math.clamp(row, 0, Math.max(0, colors.entries.size() - capacity));
		for (int i = row; i < Math.min(colors.entries.size(), row + capacity); i++) {
			int index = i;
			var entry = colors.entries.get(i);
			int y = 77 + (i - row) * ROW_HEIGHT;
			int nameX = sidebar + 36;
			int rgbX = width - 126;
			EditBox name = addRenderableWidget(new ConfigScreenEditBox(font, nameX, y + 2, rgbX - nameX - 6,
				Component.translatable("jei.settings.colorName")));
			name.setMaxLength(256);
			name.setFilter(value -> value.chars().noneMatch(c -> c == ':' || c == ',' || c == '\n' || c == '\r'));
			name.setValue(entry.name);
			name.setTooltip(Tooltip.create(Component.translatable("jei.settings.colorName")));
			EditBox rgb = addRenderableWidget(new ConfigScreenEditBox(font, rgbX, y + 2, 68,
				Component.translatable("jei.settings.colorRgb")));
			rgb.setMaxLength(6);
			rgb.setFilter(ConfigColorListDraft::isRgbInput);
			rgb.setValue(entry.rgb);
			rgb.setTooltip(Tooltip.create(Component.translatable("jei.settings.colorRgb")
				.append("\n").append(Component.literal("000000 ~ FFFFFF").withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC))));
			name.setTextColor(entry.name.isBlank() ? 0xFFFF7777 : 0xFFE0E0E0);
			rgb.setTextColor(entry.isRgbValid() ? 0xFFE0E0E0 : 0xFFFF7777);
			name.setResponder(value -> {
				entry.name = value;
				colors.update();
				reset.active = !colors.draft.isDefault();
				name.setTextColor(value.isBlank() ? 0xFFFF7777 : 0xFFE0E0E0);
			});
			rgb.setResponder(value -> {
				entry.setRgb(value);
				colors.update();
				reset.active = !colors.draft.isDefault();
				rgb.setTextColor(entry.isRgbValid() ? 0xFFE0E0E0 : 0xFFFF7777);
			});
			addButton(width - 52, y, 40, Component.translatable("jei.settings.deleteColor"), "button", null, () -> {
				colors.remove(index);
				rebuild();
			});
		}
	}

	private static Component createTooltip(ConfigDraft<?> draft) {
		return draft.config.getLocalizedName().copy()
			.append("\n").append(draft.config.getLocalizedDescription())
			.append("\n").append(Component.translatable("jei.settings.available"))
			.append("\n").append(Component.literal(draft.getValidValuesDescription())
				.withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC));
	}

	private void save() {
		if (colorDrafts.values().stream().anyMatch(colors -> !colors.isValid())) {
			error = Component.translatable("jei.settings.invalidColor").getString();
			return;
		}
		var invalid = sections.stream().flatMap(s -> s.entries().stream())
			.filter(draft -> draft.isChanged() && !draft.getErrors().isEmpty()).findFirst();
		if (invalid.isPresent()) {
			var draft = invalid.get();
			error = draft.config.getLocalizedName().getString() + ": " + String.join("; ", draft.getErrors());
			return;
		}
		sections.forEach(s -> s.entries().forEach(ConfigDraft::apply));
		if (keyBindings.stream().anyMatch(IConfigKeyBinding::isChanged)) {
			keyBindings.forEach(IConfigKeyBinding::apply);
			KeyMapping.resetMapping();
			minecraft.options.save();
		}
		minecraft.setScreen(parent);
	}

	@Override
	public void onClose() {
		if (recording != null) {
			recording = null; recordedKey = null; rebuild(); return;
		}
		if (choosing != null) {
			goBack();
		}
		else { minecraft.setScreen(parent); }
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (recording != null) {
			return true;
		}
		if (y >= 72 && y < height - 40 && vertical != 0) {
			int step = vertical > 0 ? -1 : 1;
			if (x < sidebar) {
				int next = Math.clamp(navigationRow + step, 0, Math.max(0, navigation.size() - navigationCapacity));
				if (next == navigationRow) {
					return true;
				}
				navigationRow = next;
			}
			else {
				int next = Math.clamp(row + step, 0, hiddenRows());
				if (next == row) {
					return true;
				}
				row = next;
			}
			rebuild();
			return true;
		}
		return super.mouseScrolled(x, y, horizontal, vertical);
	}

	private int hiddenRows() {
		int count = keyView ? visibleKeys.size() : colorPage != null ? colorPage.entries.size() : choosing == null ? visible.size() : listChoiceCount;
		return Math.max(0, count - capacity);
	}

	private boolean scroll(double x, double y, boolean start) {
		int hidden = hiddenRows();
		var result = start ? scrollbar.startDrag(x, y, capacity, hidden, hidden == 0 ? 0 : row / (float) hidden) :
			scrollbar.dragTo(y, capacity, hidden, hidden == 0 ? 0 : row / (float) hidden);
		if (result.handled()) {
			int next = Math.round(result.scrollOffsetY() * hidden);
			if (next != row) {
				row = next;
				rebuild();
			}
			return true;
		}
		hidden = Math.max(0, navigation.size() - navigationCapacity);
		result = start ? navigationScrollbar.startDrag(x, y, navigationCapacity, hidden, hidden == 0 ? 0 : navigationRow / (float) hidden) :
			navigationScrollbar.dragTo(y, navigationCapacity, hidden, hidden == 0 ? 0 : navigationRow / (float) hidden);
		if (result.handled()) {
			int next = Math.round(result.scrollOffsetY() * hidden);
			if (next != navigationRow) {
				navigationRow = next;
				rebuild();
			}
		}
		return result.handled();
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if (recording != null) {
			recordedKey = InputConstants.Type.MOUSE.getOrCreate(button);
			recordedModifiers = currentModifiers();
			return true;
		}
		return button == 0 && scroll(x, y, true) || super.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		if (recording != null) {
			return true;
		}
		return button == 0 && scroll(x, y, false) || super.mouseDragged(x, y, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		if (recording != null) {
			if (recordedKey != null && recordedKey.equals(InputConstants.Type.MOUSE.getOrCreate(button))) {
				finishRecording();
			}
			return true;
		}
		boolean dragging = scrollbar.isDragging() || navigationScrollbar.isDragging();
		scrollbar.stopDrag();
		navigationScrollbar.stopDrag();
		return dragging || super.mouseReleased(x, y, button);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xFF202020);
		graphics.fill(0, 0, width, 38, 0xFF303030);
		graphics.fill(0, 38, sidebar, height - 40, 0xFF292929);
		graphics.fill(0, height - 40, width, height, 0xFF303030);
		graphics.fill(sidebar, 38, sidebar + 1, height - 40, 0xFF3D3D3D);
		graphics.drawString(font, title, 12, 14, 0xFFF0F0F0, false);
		Component heading = keyView ? (keyCategory == null || !search.isBlank() ? Component.translatable("jei.settings.keybindings") : Component.translatable(keyCategory)) : choosing != null ? choosing.draft.config.getLocalizedName() : !search.isBlank() ?
			Component.translatable("jei.settings.results", visible.size()) : sections.isEmpty() || section < 0 ? title : sections.get(section).name();
		graphics.drawString(font, font.plainSubstrByWidth(heading.getString(), width - sidebar - (colorPage == null ? 24 : 114)), sidebar + 12, 49, 0xFFF0F0F0, false);
		if (colorPage != null) {
			for (int i = row; i < Math.min(colorPage.entries.size(), row + capacity); i++) {
				var entry = colorPage.entries.get(i);
				int x = sidebar + 12;
				int y = 81 + (i - row) * ROW_HEIGHT;
				graphics.fill(x, y, x + 18, y + 18, 0xFF666666);
				graphics.fill(x + 1, y + 1, x + 17, y + 17, entry.previewColor);
			}
		}
		for (int i = 0; i < rendered.size(); i++) {
			ConfigDraft<?> draft = rendered.get(i).draft();
			int y = 77 + i * ROW_HEIGHT;
			int labelWidth = Math.max(20, width - sidebar - 36 - Math.min(148, Math.max(70, (width - sidebar) / 2)));
			String name = (draft.isChanged() ? "* " : "") + draft.config.getLocalizedName().getString();
			graphics.drawString(font, font.plainSubstrByWidth(name, labelWidth), sidebar + 12, y + 7, 0xFFF0F0F0, false);
			graphics.fill(sidebar + 12, y + ROW_HEIGHT - 2, width - 12, y + ROW_HEIGHT - 1, 0xFF323232);
		}
		if (keyView) {
			for (int i = row; i < Math.min(visibleKeys.size(), row + capacity); i++) {
				var binding = visibleKeys.get(i);
				int y = 77 + (i - row) * ROW_HEIGHT;
				String name = (binding.isChanged() ? "* " : "") + Component.translatable(binding.getName()).getString();
				graphics.drawString(font, font.plainSubstrByWidth(name, Math.max(10, width - sidebar - 204)), sidebar + 12, y + 7, 0xFFF0F0F0, false);
				graphics.fill(sidebar + 12, y + ROW_HEIGHT - 2, width - 12, y + ROW_HEIGHT - 1, 0xFF323232);
			}
		}
		int hidden = hiddenRows();
		if (hidden > 0) {
			scrollbar.draw(graphics, capacity, hidden, row / (float) hidden);
		}
		int hiddenCategories = navigation.size() - navigationCapacity;
		if (hiddenCategories > 0) {
			navigationScrollbar.draw(graphics, navigationCapacity, hiddenCategories, navigationRow / (float) hiddenCategories);
		}
		Component status = error.isEmpty() ? Component.translatable("jei.settings.changed", changed + changedKeys) : Component.literal(error);
		graphics.drawString(font, font.plainSubstrByWidth(status.getString(), Math.max(0, width - 206)), 9, height - 23, error.isEmpty() ? 0xFFD9BC76 : 0xFFFF7777, false);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		if (keyView) {
			for (int i = row; i < Math.min(visibleKeys.size(), row + capacity); i++) {
				int y = 77 + (i - row) * ROW_HEIGHT;
				if (mouseX >= sidebar + 12 && mouseX < width - 182 && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
					graphics.renderTooltip(font, font.split(keyTooltips.get(i - row), Math.min(300, width - 24)), mouseX, mouseY);
				}
			}
		}
		for (int i = 0; i < rendered.size(); i++) {
			int y = 77 + i * ROW_HEIGHT;
			int controlX = width - Math.min(148, Math.max(70, (width - sidebar) / 2)) - 12;
			if (mouseX >= sidebar + 12 && mouseX < controlX && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
				graphics.renderTooltip(font, rendered.get(i).tooltip(), mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean isPauseScreen() { return false; }
}

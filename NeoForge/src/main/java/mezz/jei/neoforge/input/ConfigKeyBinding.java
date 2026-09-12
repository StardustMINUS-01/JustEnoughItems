package mezz.jei.neoforge.input;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.input.KeyNameUtil;
import mezz.jei.gui.config.screen.IConfigKeyBinding;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class ConfigKeyBinding implements IConfigKeyBinding {
	private final KeyMapping mapping;
	private final Map<KeyMapping, ConfigKeyBinding> drafts;
	private InputConstants.Key originalKey;
	private KeyModifier originalModifier;
	private InputConstants.Key key;
	private KeyModifier modifier;

	private ConfigKeyBinding(KeyMapping mapping, Map<KeyMapping, ConfigKeyBinding> drafts) {
		this.mapping = mapping;
		this.drafts = drafts;
		originalKey = key = mapping.getKey();
		originalModifier = modifier = mapping.getKeyModifier();
	}

	public static List<IConfigKeyBinding> create() {
		Map<KeyMapping, ConfigKeyBinding> drafts = new LinkedHashMap<>();
		for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
			if (mapping.getName().startsWith("key.jei.")) {
				drafts.put(mapping, new ConfigKeyBinding(mapping, drafts));
			}
		}
		return List.copyOf(drafts.values());
	}

	@Override
	public String getName() { return mapping.getName(); }
	@Override
	public String getCategory() { return mapping.getCategory(); }
	@Override
	public Component getBindingName() { return modifier.getCombinedName(key, () -> KeyNameUtil.getKeyDisplayName(key)); }
	@Override
	public boolean isChanged() { return !key.equals(originalKey) || modifier != originalModifier; }
	@Override
	public boolean isDefault() { return key.equals(mapping.getDefaultKey()) && modifier == mapping.getDefaultKeyModifier(); }
	@Override
	public void reset() { key = mapping.getDefaultKey(); modifier = mapping.getDefaultKeyModifier(); }

	@Override
	public boolean setKey(InputConstants.Key key, int modifiers) {
		int control = Minecraft.ON_OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL;
		int held = modifiers & (control | GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT);
		if (Integer.bitCount(held) > 1) {
			return false;
		}
		this.key = key;
		modifier = key.equals(InputConstants.UNKNOWN) ? KeyModifier.NONE : (held & control) != 0 ? KeyModifier.CONTROL : (held & GLFW.GLFW_MOD_SHIFT) != 0 ? KeyModifier.SHIFT : (held & GLFW.GLFW_MOD_ALT) != 0 ? KeyModifier.ALT : KeyModifier.NONE;
		if (modifier.matches(key)) {
			modifier = KeyModifier.NONE;
		}
		return true;
	}

	@Override
	public void apply() {
		if (isChanged()) {
			mapping.setKeyModifierAndCode(modifier, key);
			originalKey = key;
			originalModifier = modifier;
		}
	}

	@Override
	public List<Component> getConflicts() {
		List<Component> conflicts = new ArrayList<>();
		if (key.equals(InputConstants.UNKNOWN)) {
			return conflicts;
		}
		for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
			if (other == mapping) {
				continue;
			}
			var draft = drafts.get(other);
			var otherKey = draft == null ? other.getKey() : draft.key;
			var otherModifier = draft == null ? other.getKeyModifier() : draft.modifier;
			var context = mapping.getKeyConflictContext();
			if (otherKey.equals(InputConstants.UNKNOWN) || !(context.conflicts(other.getKeyConflictContext()) || other.getKeyConflictContext().conflicts(context))) {
				continue;
			}
			if (modifier.matches(otherKey) || otherModifier.matches(key) || key.equals(otherKey) &&
				(modifier == otherModifier || context.conflicts(KeyConflictContext.IN_GAME) && (modifier == KeyModifier.NONE || otherModifier == KeyModifier.NONE))
			) {
				conflicts.add(Component.translatable(other.getName()));
			}
		}
		return conflicts;
	}
}

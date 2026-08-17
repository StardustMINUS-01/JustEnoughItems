package mezz.jei.forge.input;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;

import mezz.jei.common.input.KeyNameUtil;
import mezz.jei.common.input.keys.IJeiKeyMappingInternal;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

public class ForgeJeiKeyMapping implements IJeiKeyMappingInternal {
	private final KeyMapping keyMapping;

	public ForgeJeiKeyMapping(KeyMapping keyMapping) {
		this.keyMapping = keyMapping;
	}

	/**
	 * Delegate to Forge's KeyMapping, which respects its KeyModifier system:
	 * a binding with modifier CONTROL (e.g. focusSearch = CTRL+F) will NOT match
	 * when the plain key is pressed without the modifier, and a plain binding
	 * (KeyModifier.NONE) only matches when no modifier key is active.
	 *
	 * This matches NeoForge 1.21.1 where isActiveAndMatches also respects the
	 * binding's modifier, so CTRL+F must not be triggered by a plain F press.
	 */
	@Override
	public boolean isActiveAndMatches(InputConstants.Key key) {
		return keyMapping.isActiveAndMatches(key);
	}

	/**
	 * Forge 1.20.1's KeyMapping checks {@code KeyModifier} when matching keys:
	 * with a plain key bound (KeyModifier.NONE), pressing SHIFT+F makes
	 * {@code keyMapping.isActiveAndMatches(F)} return false because the active modifier
	 * (SHIFT) does not equal the bound modifier (NONE).
	 *
	 * NeoForge 1.21.1's matchesIgnoringModifiers only compares the bound key code.
	 * To keep behavior identical to 1.21.1 (SHIFT+F to save a favorite recipe tree),
	 * we ignore modifiers here and compare only the bound key.
	 */
	@Override
	public boolean matchesIgnoringModifiers(InputConstants.Key key) {
		return !isUnbound() && keyMapping.getKey().equals(key);
	}

	@Override
	public boolean isUnbound() {
		return keyMapping.isUnbound();
	}

	@Override
	public Component getTranslatedKeyMessage() {
		InputConstants.Key key = keyMapping.getKey();
		return keyMapping.getKeyModifier().getCombinedName(key, () -> KeyNameUtil.getKeyDisplayName(key));
	}

	@Override
	public boolean isDown() {
		return IJeiKeyMappingInternal.isKeyDown(keyMapping.getKey());
	}

	@Override
	public IJeiKeyMappingInternal register(Consumer<KeyMapping> registerMethod) {
		registerMethod.accept(keyMapping);
		return this;
	}
}

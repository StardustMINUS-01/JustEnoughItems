package mezz.jei.gui.input.handlers;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.compat.ExternalIngredientSearchHandlerRegistry;
import mezz.jei.gui.input.ClickableIngredientInternal;
import mezz.jei.gui.input.IngredientFocusSource;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.common.input.InputPhase;
import mezz.jei.common.input.UserInput;
import mezz.jei.gui.overlay.elements.IngredientElement;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.util.stream.Stream;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.item;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FocusInputHandlerTest {
	@BeforeAll
	public static void bootStrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@ParameterizedTest
	@CsvSource({
		"KEYSYM, true, false, 1",
		"MOUSE, true, false, 1",
		"MOUSE, false, false, 0",
		"MOUSE, true, true, 0"
	})
	public void terminalSearchExecutesOnlyWhenInteractionCompletes(InputConstants.Type type, boolean inside, boolean cancel, int expectedSearches) {
		InputConstants.Key searchKey = type.getOrCreate(1);
		ITypedIngredient<?> ingredient = item(Items.DIAMOND);
		IngredientFocusSource focusSource = new IngredientFocusSource(null) {
			@Override
			public Stream<IClickableIngredientInternal<?>> getIngredientForInput(UserInput input, IInternalKeyMappings keyBindings) {
				IngredientElement<?> element = new IngredientElement<>(ingredient);
				return Stream.of(new ClickableIngredientInternal<>(element, (x, y) -> x >= 0 && x < 18 && y >= 0 && y < 18, false, false));
			}
		};
		IJeiKeyMapping matching = createKeyMapping(searchKey);
		IJeiKeyMapping noMatch = createKeyMapping(InputConstants.UNKNOWN);
		IInternalKeyMappings keyMappings = (IInternalKeyMappings) Proxy.newProxyInstance(
			IInternalKeyMappings.class.getClassLoader(),
			new Class<?>[]{IInternalKeyMappings.class},
			(proxy, method, args) -> method.getName().equals("getSearchIngredientInTerminal") ? matching : noMatch
		);
		IngredientShortcutInputHandler handler = new IngredientShortcutInputHandler(
			focusSource, null, null, null, null, null, null
		);
		int[] searches = {0};
		ExternalIngredientSearchHandlerRegistry.register((screen, searchedIngredient, simulate) -> {
			if (!simulate) {
				searches[0]++;
			}
			return searchedIngredient == ingredient;
		});

		try {
			UserInput input = new UserInput(searchKey, 8, 8, 0, InputPhase.PRESS);
			var interaction = handler.beginInput(null, input, keyMappings).orElseThrow();
			assertEquals(0, searches[0]);
			if (cancel) {
				interaction.cancel();
			} else {
				interaction.complete(input.isKeyboardInput() ? input : new UserInput(searchKey, inside ? 8 : 24, 8, 0, InputPhase.RELEASE));
			}
			assertEquals(expectedSearches, searches[0]);
		} finally {
			ExternalIngredientSearchHandlerRegistry.register((screen, searchedIngredient, simulate) -> false);
		}
	}

	private static IJeiKeyMapping createKeyMapping(InputConstants.Key key) {
		return (IJeiKeyMapping) Proxy.newProxyInstance(
			IJeiKeyMapping.class.getClassLoader(),
			new Class<?>[]{mezz.jei.common.input.keys.IJeiKeyMappingInternal.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "isActiveAndMatches", "isActiveAndMatchesShortcut" -> key.equals(args[0]);
				case "isUnbound", "isDown" -> false;
				case "getTranslatedKeyMessage" -> Component.empty();
				default -> null;
			}
		);
	}

}

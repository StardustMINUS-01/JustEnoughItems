package mezz.jei.gui.input.handlers;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.compat.ExternalIngredientSearchHandlerRegistry;
import mezz.jei.gui.input.ClickableIngredientInternal;
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.elements.IngredientElement;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.stream.Stream;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FocusInputHandlerTest {
	@BeforeAll
	public static void bootStrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void terminalSearchUsesCombinedFocusSource() {
		InputConstants.Key searchKey = InputConstants.Type.KEYSYM.getOrCreate(1);
		ITypedIngredient<?> ingredient = item(Items.DIAMOND);
		CombinedRecipeFocusSource focusSource = new CombinedRecipeFocusSource() {
			@Override
			public Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(UserInput input, IInternalKeyMappings keyBindings) {
				IngredientElement<?> element = new IngredientElement<>(ingredient);
				return Stream.of(new ClickableIngredientInternal<>(element, (x, y) -> true, false, false));
			}
		};
		IJeiKeyMapping matching = createKeyMapping(searchKey);
		IJeiKeyMapping noMatch = createKeyMapping(InputConstants.UNKNOWN);
		IInternalKeyMappings keyMappings = (IInternalKeyMappings) Proxy.newProxyInstance(
			IInternalKeyMappings.class.getClassLoader(),
			new Class<?>[]{IInternalKeyMappings.class},
			(proxy, method, args) -> method.getName().equals("getSearchIngredientInTerminal") ? matching : noMatch
		);
		FocusInputHandler handler = new IngredientShortcutInputHandler(
			focusSource, null, null, null, null, null, null
		);
		int[] searches = {0};
		ExternalIngredientSearchHandlerRegistry.register((screen, searchedIngredient, simulate) -> {
			searches[0]++;
			return searchedIngredient == ingredient;
		});

		try {
			UserInput input = new UserInput(searchKey, 0, 0, 0, InputType.IMMEDIATE);
			assertTrue(handler.handleUserInput(null, input, keyMappings).isPresent());
			assertEquals(1, searches[0]);
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

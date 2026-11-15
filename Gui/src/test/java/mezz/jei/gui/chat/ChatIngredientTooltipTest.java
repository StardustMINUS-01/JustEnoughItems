package mezz.jei.gui.chat;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.common.chat.JeiChatItemLinks;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatIngredientTooltipTest {
	@Test
	void resolvingLinksDoesNotBuildTooltipsAndUsesTheCurrentRuntime() {
		IJeiRuntime previous = Internal.getOptionalJeiRuntime().orElse(null);
		try {
			ITypedIngredient<String> first = ingredient("first");
			ITypedIngredient<String> second = ingredient("second");
			Internal.setRuntime(runtime(List.of(first, second)));
			assertSame(first, ChatIngredientTooltip.getIngredientForHoveredText(style("first")).orElseThrow());
			assertSame(second, ChatIngredientTooltip.getIngredientForHoveredText(style("second")).orElseThrow());
			assertTrue(ChatIngredientTooltip.getIngredientForHoveredText(style("missing")).isEmpty());

			ITypedIngredient<String> replacement = ingredient("second");
			Internal.setRuntime(runtime(List.of(replacement)));
			assertSame(replacement, ChatIngredientTooltip.getIngredientForHoveredText(style("second")).orElseThrow());
			Internal.setRuntime(null);
			assertTrue(ChatIngredientTooltip.getIngredientForHoveredText(style("second")).isEmpty());
		} finally {
			Internal.setRuntime(previous);
			ChatIngredientTooltip.clearSharedIngredient();
		}
	}

	private static Style style(String uid) {
		String command = JeiChatItemLinks.createShowRecipeCommand(new JeiChatItemLinks.IngredientLink("test", uid));
		return Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
	}

	private static ITypedIngredient<String> ingredient(String value) {
		return proxy(ITypedIngredient.class, (method, args) -> switch (method) {
			case "getIngredient" -> value;
			default -> throw new AssertionError(method);
		});
	}

	private static IJeiRuntime runtime(List<ITypedIngredient<String>> ingredients) {
		IIngredientType<String> type = () -> String.class;
		IIngredientHelper<String> helper = proxy(IIngredientHelper.class, (method, args) -> switch (method) {
			case "getUid" -> ((ITypedIngredient<?>) args[0]).getIngredient();
			default -> throw new AssertionError(method);
		});
		IIngredientManager manager = proxy(IIngredientManager.class, (method, args) -> switch (method) {
			case "getIngredientTypeForUid" -> Optional.of(type);
			case "getIngredientHelper" -> helper;
			case "getAllTypedIngredients" -> ingredients;
			default -> throw new AssertionError("Ingredient resolution must not request a renderer: " + method);
		});
		return proxy(IJeiRuntime.class, (method, args) -> switch (method) {
			case "getIngredientManager" -> manager;
			default -> throw new AssertionError(method);
		});
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> calls) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, args) -> calls.apply(method.getName(), args));
	}
}

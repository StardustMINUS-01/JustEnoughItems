package mezz.jei.neoforge.tests.client;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.tooltip.IInteractiveClientTooltipComponent;
import mezz.jei.api.gui.tooltip.ITooltipInteractionContext;
import mezz.jei.api.gui.tooltip.TooltipCloseReason;
import mezz.jei.api.gui.tooltip.TooltipPointerResult;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.common.chat.JeiChatItemLinkHover;
import mezz.jei.common.chat.JeiChatItemLinks;
import mezz.jei.common.gui.TooltipRenderScope;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.keys.IJeiKeyMappingInternal;
import mezz.jei.gui.chat.ChatRecipeTooltip;
import mezz.jei.gui.input.InteractiveTooltipController;
import mezz.jei.gui.input.PinnedTooltipManager;
import mezz.jei.test.client.ClientTestUtil;
import mezz.jei.test.lib.JUnitXmlTestReporter;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;

@EventBusSubscriber(modid = JeiNeoForgeClientTests.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class JeiNeoForgeClientChatTooltipTests {
	private JeiNeoForgeClientChatTooltipTests() {}

	@SubscribeEvent
	public static void registerComponents(RegisterClientTooltipComponentFactoriesEvent event) {
		event.register(RecordingComponent.class, Function.identity());
	}

	public static void run() {
		JUnitXmlTestReporter.runAndReport("neoforge-client-gametest", JeiNeoForgeClientChatTooltipTests.class.getSimpleName(),
			() -> ClientTestUtil.runOnClient(JeiNeoForgeClientChatTooltipTests::assertChatTooltipRendering));
	}

	private static void assertChatTooltipRendering(Minecraft client) {
		IJeiRuntime runtime = Internal.getJeiRuntime();
		IInternalKeyMappings keys = Internal.getKeyMappings();
		IIngredientManager manager = runtime.getIngredientManager();
		IIngredientRenderer<?> renderer = manager.getIngredientRenderer(VanillaTypes.ITEM_STACK);
		RecordingComponent component = new RecordingComponent();
		int[] builds = {0};
		IIngredientRenderer<?> recordingRenderer = proxy(IIngredientRenderer.class, (method, args) -> {
			Object result = invoke(renderer, method, args);
			if (method.getName().equals("getTooltip") && args[0] instanceof ITooltipBuilder builder) {
				builds[0]++;
				builder.add(component);
			}
			return result;
		});
		IIngredientManager recordingManager = proxy(IIngredientManager.class, (method, args) -> method.getName().equals("getIngredientRenderer") && args[0] == VanillaTypes.ITEM_STACK
			? recordingRenderer : invoke(manager, method, args));
		IJeiRuntime recordingRuntime = proxy(IJeiRuntime.class, (method, args) -> method.getName().equals("getIngredientManager") ? recordingManager : invoke(runtime, method, args));
		ChatRecipeTooltip tooltip = new ChatRecipeTooltip();
		InteractiveTooltipController host = InteractiveTooltipController.INSTANCE;
		boolean windowActive = client.isWindowActive();
		try {
			// Automated clients may open behind other windows; exercise both focus states explicitly.
			client.setWindowActive(true);
			Internal.setRuntime(recordingRuntime);
			ChatScreen screen = new ChatScreen("");
			client.setScreen(screen);
			Style style = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
				JeiChatItemLinks.createShowRecipeCommand(new JeiChatItemLinks.IngredientLink(VanillaTypes.ITEM_STACK.getUid(), "minecraft:stone"))));
			client.gui.getChat().addMessage(Component.literal("Chat tooltip regression").withStyle(style));
			hoverLink(tooltip, screen, style);
			check(tooltip.isUnpinnedIngredient(), "Expected an ordinary chat ingredient");
			check(builds[0] == 0, "Resolving the hovered ingredient built a rich tooltip");
			GuiGraphics graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
			var data = tooltip.getIngredientTooltip().orElseThrow();
			tooltip.drawIngredientTooltip(graphics, data);
			check(builds[0] == 1 && component.draws == 1, "First hover must build and draw once");
			check(!tooltip.draw(graphics, 0, 0) && component.draws == 1, "Post rendering duplicated an ordinary tooltip");
			check(component.context != null && !component.context.isPinned() && component.mounts == 0, "Hover must use a passive interaction context");
			check(TooltipRenderScope.current() == null, "Tooltip scope leaked after drawing");
			check(graphics.pose().clear(), "Normal tooltip drawing leaked pose state");

			// Simulate only the host's pin key; native Shift polling is covered by the manual chat test.
			IJeiKeyMappingInternal pin = proxy(IJeiKeyMappingInternal.class, (method, args) -> method.getName().equals("isDown") ? true : invoke(keys.getPauseRecipeCycling(), method, args));
			Internal.setKeyMappings(proxy(IInternalKeyMappings.class, (method, args) -> method.getName().equals("getPauseRecipeCycling") ? pin : invoke(keys, method, args)));
			PinnedTooltipManager.opened(tooltip);
			for (int frame = 0; frame < 2; frame++) {
				var pinnedData = tooltip.getIngredientTooltip().orElseThrow();
				PinnedTooltipManager.draw(tooltip, () -> tooltip.drawIngredientTooltip(graphics, pinnedData));
			}
			check(component.mounts == 1 && component.draws == 3, "An attached tooltip must keep one session and draw once per frame");
			host.draw(graphics);
			check(component.draws == 3 && PinnedTooltipManager.getActiveHolder() == tooltip,
				"Attached tooltip lost its owner: draws=" + component.draws + ", closes=" + component.closes +
					", reason=" + component.closeReason + ", focused=" + client.isWindowActive());
			var bounds = component.context.getVisibleBounds();
			double x = bounds.left() + 1;
			double y = bounds.top() + 1;
			check(host.mousePressed(x, y, 0, 0), "Interactive component did not consume its press");
			check(host.mouseReleased(x, y, 0, 0), "Interactive component lost its release");
			check(!host.mouseReleased(x, y, 0, 0) && component.presses == 1 && component.releases == 1, "Component input was delivered twice");
			client.setWindowActive(false);
			host.tick();
			check(component.closes == 1 && component.closeReason == TooltipCloseReason.FOCUS_LOST && PinnedTooltipManager.getActiveHolder() == null,
				"Losing focus did not release the attached session");
			client.setWindowActive(true);
			tooltip.clear();
			check(component.closes == 1 && PinnedTooltipManager.getActiveHolder() == null, "Closing chat did not release the attached session");

			Internal.setKeyMappings(keys);
			hoverLink(tooltip, screen, style);
			component.crash = true;
			try {
				tooltip.drawIngredientTooltip(graphics, tooltip.getIngredientTooltip().orElseThrow());
				throw new AssertionError("Expected the component draw to fail");
			} catch (ReportedException expected) {
				String details = expected.getReport().getDetails();
				check(details.contains("Ingredient") && details.contains("minecraft:stone") && details.contains("tooltip"), "Ingredient and tooltip crash context was lost");
			}
			// Vanilla tooltip rendering does not unwind its pose on failure; JEI must still unwind its scope.
			check(TooltipRenderScope.current() == null, "Failed drawing leaked tooltip scope");
		} finally {
			tooltip.clear();
			client.setWindowActive(windowActive);
			Internal.setKeyMappings(keys);
			Internal.setRuntime(runtime);
			client.setScreen(null);
		}
	}

	private static void hoverLink(ChatRecipeTooltip tooltip, ChatScreen screen, Style style) {
		for (int y = screen.height - 41; y >= 0; y--) {
			for (int x = 0; x < screen.width; x++) {
				if (JeiChatItemLinkHover.getHoveredStyle(screen, x, y).filter(style::equals).isPresent()) {
					tooltip.update(screen, x, y);
					return;
				}
			}
		}
		throw new AssertionError("Expected the test chat link to be visible");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private interface Call {
		Object invoke(Method method, Object[] args) throws Throwable;
	}

	private static <T> T proxy(Class<T> type, Call call) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> call.invoke(method, args)));
	}

	private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
		try {
			return method.invoke(target, args);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	private static final class RecordingComponent implements TooltipComponent, IInteractiveClientTooltipComponent {
		private ITooltipInteractionContext context;
		private int draws, mounts, closes, presses, releases;
		private boolean crash;
		private TooltipCloseReason closeReason;

		@Override
		public int getWidth(Font font) { return 40; }
		@Override
		public int getHeight() { return 20; }
		@Override
		public void onLayout(ITooltipInteractionContext context) { this.context = context; }
		@Override
		public void onMounted(ITooltipInteractionContext context) { mounts++; }
		@Override
		public void onClosed(ITooltipInteractionContext context, TooltipCloseReason reason) { closes++; closeReason = reason; }
		@Override
		public TooltipPointerResult mousePressed(ITooltipInteractionContext context, int button, int modifiers) { presses++; return TooltipPointerResult.CAPTURE; }
		@Override
		public boolean mouseReleased(ITooltipInteractionContext context, int button, int modifiers) { releases++; return true; }
		@Override
		public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
			check(TooltipRenderScope.current() != null && TooltipRenderScope.current().ingredient() != null, "Missing ingredient render scope");
			draws++;
			if (crash) {
				throw new IllegalStateException("Chat tooltip regression component");
			}
		}
	}
}

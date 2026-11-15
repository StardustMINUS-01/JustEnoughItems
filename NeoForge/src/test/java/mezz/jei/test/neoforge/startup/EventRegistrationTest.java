package mezz.jei.test.neoforge.startup;

import mezz.jei.gui.input.ClientInputHandler;
import mezz.jei.gui.input.handlers.WorldInputHandler;
import mezz.jei.gui.startup.JeiEventHandlers;
import mezz.jei.neoforge.events.RuntimeEventSubscriptions;
import mezz.jei.neoforge.startup.EventRegistration;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventRegistrationTest {
	@Test
	public void worldInputSubscriptionsAreRemovedBetweenRuntimes() {
		Set<Object> listeners = new HashSet<>();
		IEventBus eventBus = (IEventBus) Proxy.newProxyInstance(
			IEventBus.class.getClassLoader(), new Class<?>[]{IEventBus.class},
			(proxy, method, args) -> {
				switch (method.getName()) {
					case "addListener" -> {
						assertEquals(InputEvent.Key.class, args[2]);
						listeners.add(args[3]);
					}
					case "unregister" -> assertTrue(listeners.remove(args[0]));
					default -> throw new UnsupportedOperationException(method.getName());
				}
				return null;
			}
		);
		RuntimeEventSubscriptions subscriptions = new RuntimeEventSubscriptions(eventBus);
		for (int runtime = 0; runtime < 2; runtime++) {
			EventRegistration.registerWorldInputHandler(subscriptions, new WorldInputHandler(null, null, null, null));
			assertEquals(1, listeners.size());
			subscriptions.clear();
			assertTrue(listeners.isEmpty());
			assertTrue(subscriptions.isEmpty());
		}
	}

	@Test
	public void registersOverlayRenderingAndBookmarkInputEvents() {
		Map<Class<?>, EventPriority> registeredEvents = new HashMap<>();
		IEventBus eventBus = (IEventBus) Proxy.newProxyInstance(
			IEventBus.class.getClassLoader(), new Class<?>[]{IEventBus.class},
			(proxy, method, args) -> {
				if (method.getName().equals("addListener")) {
					registeredEvents.put((Class<?>) args[2], (EventPriority) args[0]);
					return null;
				}
				throw new UnsupportedOperationException(method.getName());
			}
		);
		// Capture registration without dispatching events into game handlers.
		ClientInputHandler clientInputHandler = new ClientInputHandler(null, null);
		EventRegistration.registerEvents(new RuntimeEventSubscriptions(eventBus), new JeiEventHandlers(null, clientInputHandler, null));

		assertEquals(EventPriority.HIGHEST, registeredEvents.get(ContainerScreenEvent.Render.Background.class));
		assertEquals(EventPriority.LOWEST, registeredEvents.get(ContainerScreenEvent.Render.Foreground.class));
		assertEquals(EventPriority.HIGHEST, registeredEvents.get(ScreenEvent.Render.Post.class));
		assertEquals(EventPriority.NORMAL, registeredEvents.get(ClientTickEvent.Post.class));
		assertEquals(EventPriority.NORMAL, registeredEvents.get(ScreenEvent.KeyReleased.Pre.class));
		assertEquals(EventPriority.NORMAL, registeredEvents.get(ScreenEvent.MouseDragged.Pre.class));
	}
}

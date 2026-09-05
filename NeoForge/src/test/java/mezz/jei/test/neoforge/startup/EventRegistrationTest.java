package mezz.jei.test.neoforge.startup;

import mezz.jei.gui.startup.JeiEventHandlers;
import mezz.jei.neoforge.events.RuntimeEventSubscriptions;
import mezz.jei.neoforge.startup.EventRegistration;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventRegistrationTest {
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
		EventRegistration.registerEvents(new RuntimeEventSubscriptions(eventBus), new JeiEventHandlers(null, null, null, null));

		assertEquals(EventPriority.HIGHEST, registeredEvents.get(ContainerScreenEvent.Render.Background.class));
		assertEquals(EventPriority.LOWEST, registeredEvents.get(ContainerScreenEvent.Render.Foreground.class));
		assertEquals(EventPriority.HIGHEST, registeredEvents.get(ScreenEvent.Render.Post.class));
		assertEquals(EventPriority.NORMAL, registeredEvents.get(ClientTickEvent.Post.class));
		assertEquals(EventPriority.NORMAL, registeredEvents.get(ScreenEvent.KeyReleased.Pre.class));
		assertEquals(EventPriority.NORMAL, registeredEvents.get(ScreenEvent.MouseDragged.Pre.class));
	}
}

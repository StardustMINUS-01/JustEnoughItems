package mezz.jei.test.gui.config;

import mezz.jei.common.util.ReflectionCache;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionCacheTest {
	@Test
	public void findMethodReturnsAndCachesPublicMethod() {
		Method first = ReflectionCache.findMethod(String.class, "length").orElseThrow();
		Method second = ReflectionCache.findMethod(String.class, "length").orElseThrow();

		Assertions.assertSame(first, second);
		Assertions.assertEquals("length", first.getName());
	}

	@Test
	public void findMethodReturnsEmptyForMissingMethod() {
		Assertions.assertTrue(ReflectionCache.findMethod(String.class, "notAMethod").isEmpty());
	}

	@Test
	public void findFieldWalksClassHierarchy() {
		Field field = ReflectionCache.findField(Child.class, "hidden").orElseThrow();

		Assertions.assertEquals("hidden", field.getName());
		Assertions.assertEquals(Base.class, field.getDeclaringClass());
	}

	@Test
	public void getMethodsReturnsPublicMethods() {
		Method[] methods = ReflectionCache.getMethods(String.class);

		Assertions.assertTrue(java.util.Arrays.stream(methods).anyMatch(method -> method.getName().equals("length")));
	}

	private static class Base {
		@SuppressWarnings("unused")
		private int hidden;
	}

	private static class Child extends Base {
	}
}

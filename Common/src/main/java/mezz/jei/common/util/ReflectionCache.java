package mezz.jei.common.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ReflectionCache {
	private static final ConcurrentMap<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();
	private static final ConcurrentMap<String, Optional<Field>> FIELDS = new ConcurrentHashMap<>();
	private static final ConcurrentMap<Class<?>, Method[]> METHODS_BY_CLASS = new ConcurrentHashMap<>();

	private ReflectionCache() {
	}

	public static Optional<Method> findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
		String key = type.getName() + "#" + name + Arrays.toString(parameterTypes);
		return METHODS.computeIfAbsent(key, ignored -> {
			try {
				Method method = type.getMethod(name, parameterTypes);
				method.trySetAccessible();
				return Optional.of(method);
			} catch (ReflectiveOperationException | SecurityException e) {
				return Optional.empty();
			}
		});
	}

	public static Optional<Field> findField(Class<?> type, String name) {
		String key = type.getName() + "#" + name;
		return FIELDS.computeIfAbsent(key, ignored -> {
			Class<?> current = type;
			while (current != null) {
				try {
					Field field = current.getDeclaredField(name);
					field.trySetAccessible();
					return Optional.of(field);
				} catch (NoSuchFieldException e) {
					current = current.getSuperclass();
				} catch (ReflectiveOperationException | SecurityException e) {
					return Optional.empty();
				}
			}
			return Optional.empty();
		});
	}

	public static Method[] getMethods(Class<?> type) {
		return METHODS_BY_CLASS.computeIfAbsent(type, Class::getMethods);
	}
}

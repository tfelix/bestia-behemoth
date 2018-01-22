package net.bestia.entity.component;

import org.reflections.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.reflections.ReflectionUtils.*;

public class ScriptPropertyAccessor {

	private Map<String, ScriptPropertyAccessor> properties = new HashMap<>();
	private Map<String, Method> setters = new HashMap<>();
	private Map<String, Method> getters = new HashMap<>();

	public ScriptPropertyAccessor(Class<?> clazz) {

		final Set<Method> publicMethods = getAllMethods(clazz,
				withModifier(Modifier.PUBLIC));

		publicMethods.forEach(method -> {
			if (isSetter(method)) {
				save(method, setters);
			} else if (isGetter(method)) {
				save(method, getters);
			}
		});

		final Set<Field> fields = getAllFields(clazz,
				ReflectionUtils.withAnnotation(ScriptProperty.class));

		fields.forEach(field -> {
			properties.put(field.getName(), new ScriptPropertyAccessor(field.getType()));
		});
	}

	private void save(Method m, Map<String, Method> save) {
		final String methodName = m.getName().toLowerCase()
				.replace("get", "")
				.replace("set", "");
		save.put(methodName, m);
	}

	private boolean isGetter(Method method) {
		final boolean isNamedGet = method.getName().toLowerCase().startsWith("get");
		final boolean hasNoParam = method.getParameterTypes().length == 0;
		final boolean hasNonVoidReturn = !method.getReturnType().equals(Void.TYPE);
		final ScriptProperty scriptProp = method.getDeclaredAnnotation(ScriptProperty.class);
		final boolean isAnnotatedGetter = scriptProp != null
				&& scriptProp.accessor() != null
				&& scriptProp.accessor() == ScriptProperty.Accessor.GETTER;
		return (isNamedGet || isAnnotatedGetter) && hasNoParam && hasNonVoidReturn;
	}

	private boolean isSetter(Method method) {
		final boolean isNamedSet = method.getName().toLowerCase().startsWith("set");
		final boolean hasOneParam = method.getParameterTypes().length == 1;
		final boolean hasVoidReturn = method.getReturnType().equals(Void.TYPE);
		final ScriptProperty scriptProp = method.getDeclaredAnnotation(ScriptProperty.class);
		final boolean isAnnotatedGetter = scriptProp != null
				&& scriptProp.accessor() == ScriptProperty.Accessor.SETTER;
		return (isNamedSet || isAnnotatedGetter) && hasOneParam && hasVoidReturn;
	}

	public boolean set(String key, Object obj, Object value) {
		final String[] keys = key.split("\\.");
		final String localKey = keys[0];

		if (keys.length == 1) {
			return setLocalValue(localKey, obj, value);
		} else {
			final String reducedKey = Stream.of(keys)
					.skip(1)
					.collect(Collectors.joining("."));
			final ScriptPropertyAccessor accessor = properties.get(localKey);
			final Object newObj = getLocalValue(localKey, obj);
			return accessor.set(reducedKey, newObj, value);
		}
	}

	public Object get(String key, Object obj) {
		if (obj == null) {
			return null;
		}

		final String[] keys = key.split("\\.");
		final String localKey = keys[0];

		if (keys.length == 1) {
			return getLocalValue(localKey, obj);
		} else {
			final String reducedKey = Stream.of(keys)
					.skip(1)
					.collect(Collectors.joining("."));
			final ScriptPropertyAccessor accessor = properties.get(localKey);
			final Object newObj = getLocalValue(localKey, obj);
			return accessor.get(reducedKey, newObj);
		}
	}

	private boolean setLocalValue(String propertyName, Object obj, Object value) {
		if(obj == null || propertyName == null) {
			return false;
		}

		final Method setter = setters.get(propertyName);
		if(setter == null) {
			return false;
		}

		try {
			setter.invoke(obj, value);
			return true;
		} catch (IllegalAccessException
				| IllegalArgumentException
				| InvocationTargetException e) {
			return false;
		}
	}

	private Object getLocalValue(String propertyName, Object obj) {
		if(propertyName == null) {
			return null;
		}

		final Method getter = getters.get(propertyName);
		if (getter == null) {
			return null;
		}

		try {
			return getter.invoke(obj);
		} catch (IllegalAccessException | InvocationTargetException e) {
			return null;
		}
	}
}

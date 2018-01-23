package net.bestia.entity.component;

import com.fasterxml.jackson.databind.util.BeanUtil;
import org.reflections.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.reflections.ReflectionUtils.*;

/**
 * This class allows access to properties annotated with @{@link ScriptProperty}. These annotations
 * are usually found at components which are accessible to scripts by this way.
 */
public class ScriptPropertyAccessor {

	private static final Set<Class<?>> WRAPPER_TYPES = getWrapperTypes();

	private static Set<Class<?>> getWrapperTypes() {
		Set<Class<?>> ret = new HashSet<>();
		ret.add(Boolean.class);
		ret.add(Character.class);
		ret.add(Byte.class);
		ret.add(Short.class);
		ret.add(Integer.class);
		ret.add(Long.class);
		ret.add(Float.class);
		ret.add(Double.class);
		ret.add(Void.class);
		ret.add(String.class);
		return ret;
	}

	private static boolean isPrimitive(Class<?> clazz) {
		return WRAPPER_TYPES.contains(clazz) || clazz.isPrimitive();
	}

	private Map<String, ScriptPropertyAccessor> properties = new HashMap<>();
	private Map<String, Method> setters = new HashMap<>();
	private Map<String, Method> getters = new HashMap<>();

	public ScriptPropertyAccessor(Class<?> clazz) {

		final Set<Field> annotatedFields = getAllFields(clazz,
				withAnnotation(ScriptProperty.class));

		annotatedFields.forEach(field -> {

			final ScriptProperty scriptProperty = field.getAnnotation(ScriptProperty.class);
			final String annotatedName = scriptProperty.value();

			if(!isPrimitive(field.getType())) {
				getSetter(clazz, field).ifPresent(setter -> {
					if(annotatedName.length() > 0) {
						saveChild(annotatedName, field.getType());
					} else {
						saveChild(createMethodName(setter), field.getType());
					}
				});

				getGetter(clazz, field).ifPresent(getter -> {
					if(annotatedName.length() > 0) {
						saveChild(annotatedName, field.getType());
					} else {
						saveChild(createMethodName(getter), field.getType());
					}
				});
			} else {
				getSetter(clazz, field).ifPresent(setter -> {
					if(annotatedName.length() > 0) {
						save(annotatedName, setter, setters);
					} else {
						save(createMethodName(setter), setter, setters);
					}
				});

				getGetter(clazz, field).ifPresent(getter -> {
					if(annotatedName.length() > 0) {
						save(annotatedName, getter, getters);
					} else {
						save(createMethodName(getter), getter, getters);
					}
				});
			}

			if(Modifier.isPublic(field.getModifiers())) {
				properties.put(field.getName(), new ScriptPropertyAccessor(field.getType()));
			}
		});

		final Set<Method> publicMethods = getAllMethods(clazz,
				withModifier(Modifier.PUBLIC), withAnnotation(ScriptProperty.class));

		publicMethods.forEach(method -> {
			if (isSetter(method)) {
				save(createMethodName(method), method, setters);
			} else if (isGetter(method)) {
				save(createMethodName(method), method, getters);
			}
		});
	}

	private void saveChild(String acessorName, Class<?> nextType) {
		if(properties.containsKey(acessorName)) {
			return;
		}
		properties.put(acessorName, new ScriptPropertyAccessor(nextType));
	}

	private String makeFirstUpperCase(String name) {
		return name.substring(0, 1).toUpperCase() + name.substring(1);
	}

	@SuppressWarnings("unchecked")
	private Optional<Method> getGetter(Class<?> clazz, Field field) {
		final String fieldUpperCase = makeFirstUpperCase(field.getName());
		final String getterName = "set" + fieldUpperCase;
		final Set<Method> methods = getMethods(clazz, withName(getterName));
		return methods.stream().findFirst();
	}

	@SuppressWarnings("unchecked")
	private Optional<Method> getSetter(Class<?> clazz, Field field) {
		final String fieldUpperCase = makeFirstUpperCase(field.getName());
		final String getterName = "get" + fieldUpperCase;
		final Set<Method> methods = getMethods(clazz, withName(getterName));
		return methods.stream().findFirst();
	}

	private String createMethodName(Method m) {
		final ScriptProperty scriptProp = m.getDeclaredAnnotation(ScriptProperty.class);
		if(scriptProp != null && scriptProp.value().length() > 0) {
			return scriptProp.value();
		} else {
			return m.getName().toLowerCase()
					.replace("get", "")
					.replace("set", "");
		}
	}

	private void save(String annotatedName, Method m, Map<String, Method> save) {
		save.put(annotatedName, m);
	}

	private boolean isGetter(Method method) {
		final boolean isNamedGet = method.getName().toLowerCase().startsWith("get");
		final boolean hasNoParam = method.getParameterTypes().length == 0;
		final boolean hasNonVoidReturn = !method.getReturnType().equals(Void.TYPE);
		final ScriptProperty scriptProp = method.getDeclaredAnnotation(ScriptProperty.class);
		final boolean isAnnotatedGetter = scriptProp != null
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
			if(accessor == null) {
				return null;
			}
			final Object newObj = getLocalValue(localKey, obj);
			return accessor.get(reducedKey, newObj);
		}
	}

	private boolean setLocalValue(String propertyName, Object obj, Object value) {
		if (obj == null || propertyName == null) {
			return false;
		}

		final Method setter = setters.get(propertyName);
		if (setter == null) {
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
		if (propertyName == null) {
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

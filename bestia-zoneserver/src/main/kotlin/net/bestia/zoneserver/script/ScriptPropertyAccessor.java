package net.bestia.zoneserver.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private static final Logger LOG = LoggerFactory.getLogger(ScriptPropertyAccessor.class);
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

  private static class AccessTuple {
    public final Method objectGetter;
    public final ScriptPropertyAccessor accessor;

    public AccessTuple(Method objectGetter, ScriptPropertyAccessor accessor) {
      this.objectGetter = objectGetter;
      this.accessor = accessor;
    }
  }

  private Map<String, AccessTuple> childs = new HashMap<>();
  private Map<String, Field> properties = new HashMap<>();
  private Map<String, Method> setters = new HashMap<>();
  private Map<String, Method> getters = new HashMap<>();

  public ScriptPropertyAccessor(Class<?> clazz) {

    @SuppressWarnings("unchecked") final Set<Field> annotatedFields = getAllFields(clazz,
            withAnnotation(ScriptProperty.class));

    annotatedFields.forEach(field -> {

      final ScriptProperty scriptProperty = field.getAnnotation(ScriptProperty.class);
      final String annotatedName = scriptProperty.value();

      if (!isPrimitive(field.getType())) {
        getGetter(clazz, field).ifPresent(getter -> {
          if (annotatedName.length() > 0) {
            saveChild(annotatedName, field.getType(), getter);
          } else {
            saveChild(createMethodName(getter), field.getType(), getter);
          }
        });
      } else {
        getSetter(clazz, field).ifPresent(setter -> {
          if (annotatedName.length() > 0) {
            save(annotatedName, setter, setters);
          } else {
            save(createMethodName(setter), setter, setters);
          }
        });

        getGetter(clazz, field).ifPresent(getter -> {
          if (annotatedName.length() > 0) {
            save(annotatedName, getter, getters);
          } else {
            save(createMethodName(getter), getter, getters);
          }
        });

        if (Modifier.isPublic(field.getModifiers())) {
          if (annotatedName.length() > 0) {
            properties.put(annotatedName, field);
          } else {
            properties.put(field.getName(), field);
          }
        }
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

  private void saveChild(String acessorName, Class<?> nextType, Method getter) {
    if (childs.containsKey(acessorName)) {
      return;
    }
    final ScriptPropertyAccessor accessor = new ScriptPropertyAccessor(nextType);
    final AccessTuple tuple = new AccessTuple(getter, accessor);
    childs.put(acessorName, tuple);
  }

  private String makeFirstUpperCase(String name) {
    return name.substring(0, 1).toUpperCase() + name.substring(1);
  }

  @SuppressWarnings("unchecked")
  private Optional<Method> getGetter(Class<?> clazz, Field field) {
    final String fieldUpperCase = makeFirstUpperCase(field.getName());
    final String getterName = "get" + fieldUpperCase;
    final Set<Method> methods = getMethods(clazz, withName(getterName));
    return methods.stream().findFirst();
  }

  @SuppressWarnings("unchecked")
  private Optional<Method> getSetter(Class<?> clazz, Field field) {
    final String fieldUpperCase = makeFirstUpperCase(field.getName());
    final String setterName = "set" + fieldUpperCase;
    final Set<Method> methods = getMethods(clazz, withName(setterName));
    return methods.stream().findFirst();
  }

  private String createMethodName(Method m) {
    final ScriptProperty scriptProp = m.getDeclaredAnnotation(ScriptProperty.class);
    if (scriptProp != null && scriptProp.value().length() > 0) {
      return scriptProp.value();
    } else {
      return m.getName().toLowerCase()
              .replace("get", "")
              .replace("set", "");
    }
  }

  private void save(String annotatedName, Method m, Map<String, Method> save) {
    if (save.containsKey(annotatedName)) {
      LOG.warn("Property/Method {}/{} was annotated twice and is already referenced.",
              annotatedName, m.getName());
    }
    save.put(annotatedName, m);
  }

  private boolean isGetter(Method method) {
    final boolean isNamedGet = method.getName().toLowerCase().startsWith("get");
    final boolean hasNoParam = method.getParameterTypes().length == 0;
    final boolean hasNonVoidReturn = !method.getReturnType().equals(Void.TYPE);
    final ScriptProperty scriptProp = method.getDeclaredAnnotation(ScriptProperty.class);
    final boolean isAnnotatedGetter = scriptProp != null
            && scriptProp.accessor() == ScriptAccessor.GETTER;
    return (isNamedGet || isAnnotatedGetter) && hasNoParam && hasNonVoidReturn;
  }

  private boolean isSetter(Method method) {
    final boolean isNamedSet = method.getName().toLowerCase().startsWith("set");
    final boolean hasOneParam = method.getParameterTypes().length == 1;
    final boolean hasVoidReturn = method.getReturnType().equals(Void.TYPE);
    final ScriptProperty scriptProp = method.getDeclaredAnnotation(ScriptProperty.class);
    final boolean isAnnotatedGetter = scriptProp != null
            && scriptProp.accessor() == ScriptAccessor.SETTER;
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
      final AccessTuple tuple = childs.get(localKey);
      if (tuple == null) {
        return false;
      }
      try {
        final Object childObj = tuple.objectGetter.invoke(obj);
        return tuple.accessor.set(reducedKey, childObj, value);
      } catch (IllegalAccessException | InvocationTargetException e) {
        return false;
      }
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
      final AccessTuple tuple = childs.get(localKey);
      if (tuple == null) {
        return null;
      }
      try {
        final Object childObj = tuple.objectGetter.invoke(obj);
        return tuple.accessor.get(reducedKey, childObj);
      } catch (IllegalAccessException | InvocationTargetException e) {
        return null;
      }
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

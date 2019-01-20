package net.bestia.model.util

import org.reflections.Reflections

import java.lang.reflect.Modifier
import java.util.HashSet
import java.util.stream.Collectors

/**
 * This class can load instances of sub types of a given base-type. Because of
 * type erasure using this class is a bit cumbersome. Instance it with the type
 * of the base class as well as the variant of the base class. To search for the
 * classes then call the appropriate method.
 *
 * @author Thomas Felix
 */
class PackageLoader<BaseT>(
    private val typeParameterClass: Class<BaseT>,
    private val packageName: String
) {
  private val hasLoaded = false
  private val subclasses = HashSet<Class<out BaseT>>()

  /**
   * Returns a Set of all the subclasses for the given super-type and package.
   * This will return ALL subtypes (even abstract ones).
   *
   * @return The set of subclasses.
   */
  val subClasses: Set<Class<out BaseT>>
    get() {

      if (!hasLoaded) {
        load()
      }

      return subclasses
    }

  /**
   * Returns only concrete subsclasses which can be instantiated.
   */
  val concreteSubClasses: Set<Class<out BaseT>>
    get() {
      if (!hasLoaded) {
        load()
      }

      return subclasses.stream()
          .filter { x -> !Modifier.isAbstract(x.modifiers) }
          .collect<Set<Class<out BaseT>>, Any>(Collectors.toSet())
    }

  /**
   * Tries to instantiate the given sub-types of objects if this is needed. Of
   * course the objects need a std. ctor in order for this to work.
   *
   * @return The Set of instantiated objects.
   */
  // Dont instance abstract classes.
  val subObjects: Set<BaseT>
    get() {
      if (!hasLoaded) {
        load()
      }

      val objInstances = HashSet<BaseT>()
      for (clazz in subclasses) {
        if (Modifier.isAbstract(clazz.modifiers)) {
          LOG.trace("Can not instantiate (is Abstract) : {}", clazz.toString())
          continue
        }

        try {
          val extra = clazz.newInstance()
          objInstances.add(extra)
        } catch (e: InstantiationException) {
          LOG.error("Can not instantiate (has no std. ctor.): {}", clazz.toString(), e)
        } catch (e: IllegalAccessException) {
          LOG.error("Can not instantiate (has no std. ctor.): {}", clazz.toString(), e)
        }

      }

      return objInstances
    }

  private fun load() {
    val reflections = Reflections(packageName)
    subclasses.addAll(reflections.getSubTypesOf(typeParameterClass))
  }

  /**
   * Returns true if all referenced subclasses have at least a standard ctor.
   * false if otherwise. Useful for fast unit testing if all classes obtain a
   * std. ctor and fail the test otherwise.
   *
   * @return TRUE if all referenced subclasses have at least a std. ctor.
   * FALSE otherwise.
   */
  fun haveAllStdCtor(): Boolean {
    if (!hasLoaded) {
      load()
    }

    for (clazz in subclasses) {
      // Dont instance abstract classes.
      if (Modifier.isAbstract(clazz.modifiers)) {
        continue
      }

      try {
        clazz.getConstructor()
      } catch (e: NoSuchMethodException) {
        LOG.warn("Class {} has no accessible std. ctor.", clazz.simpleName, e)
        return false
      } catch (e: SecurityException) {
        LOG.warn("Class {} has no accessible std. ctor.", clazz.simpleName, e)
        return false
      }

    }
    return true
  }
}
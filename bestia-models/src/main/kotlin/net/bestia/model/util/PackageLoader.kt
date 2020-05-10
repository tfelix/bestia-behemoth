package net.bestia.model.util

import org.reflections.Reflections

import java.lang.reflect.Modifier
import java.util.HashSet

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
  val subClasses by lazy { load() }

  /**
   * Returns only concrete subsclasses which can be instantiated.
   */
  val concreteSubClasses: Set<Class<out BaseT>>
    get() {
      return subClasses.asSequence().filter { !Modifier.isAbstract(it.modifiers) }.toSet()
    }

  /**
   * Tries to instantiate the given sub-types of objects if this is needed. Of
   * course the objects need a std. ctor in order for this to work.
   *
   * @return The Set of instantiated objects.
   */
  val subObjects: Set<BaseT>
    get() {
      val objInstances = HashSet<BaseT>()
      for (clazz in subClasses) {
        if (Modifier.isAbstract(clazz.modifiers)) {
          LOG.trace("Can not instantiate (is Abstract) : {}", clazz.toString())
          continue
        }

        try {
          val extra = clazz.getDeclaredConstructor().newInstance()
          objInstances.add(extra)
        } catch (e: InstantiationException) {
          LOG.error("Can not instantiate (has no std. ctor.): {}", clazz.toString(), e)
        } catch (e: IllegalAccessException) {
          LOG.error("Can not instantiate (has no std. ctor.): {}", clazz.toString(), e)
        }
      }

      return objInstances
    }

  private fun load(): Set<Class<out BaseT>> {
    val reflections = Reflections(packageName)
    return reflections.getSubTypesOf(typeParameterClass).toSet()
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
    for (clazz in subClasses) {
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
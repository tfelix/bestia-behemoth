package net.bestia.zoneserver.actor

import akka.actor.Actor
import akka.actor.IndirectActorProducer
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationContext
import java.lang.reflect.Constructor
import java.util.*

private val LOG = KotlinLogging.logger { }

/**
 * An actor producer that lets Spring create the Akka Actor instances.
 */
internal class SpringActorProducer(
    private val applicationContext: ApplicationContext,
    private val actorBeanClass: Class<out Actor>,
    private val args: Array<*>
) : IndirectActorProducer {

  /**
   * It is used by Spring.
   */
  @Suppress("unused")
  constructor(
      applicationContext: ApplicationContext,
      actorBeanClass: Class<out Actor>
  ) : this(applicationContext, actorBeanClass, emptyArray<Any>())

  private fun getAnnotatedCtor(ctors: Array<Constructor<*>>): Constructor<*>? {
    return ctors.find { it.isAnnotationPresent(Autowired::class.java) }
  }

  private fun getZeroArgCtor(ctors: Array<Constructor<*>>): Constructor<*>? {
    return ctors.find { it.parameterCount == 0 }
  }

  private fun getOnlyCtor(ctors: Array<Constructor<*>>): Constructor<*>? {
    return if (ctors.size == 1) {
      ctors[0]
    } else {
      null
    }
  }

  /**
   * This will create all arguments since we need all ctor arguments present
   * inside the array so spring can create the bean. We will combine the
   * arguments provided by the user and the ones needed by the ctor.
   *
   * @return All arguments for the bean ctor invocation.
   */
  private val allCtorArgs: Array<Any>
    @Throws(ClassNotFoundException::class)
    get() {
      val ctors = actorBeanClass.constructors
      val availableArgsClasses = args
          .asSequence()
          .map { it!!.javaClass }
          .toList()
      val autoCtor: Constructor<*>? = getAnnotatedCtor(ctors)
          ?: getZeroArgCtor(ctors)
          ?: getOnlyCtor(ctors)

      if (autoCtor == null) {
        LOG.warn("No Ctor with Autowire annotation found. Can not create bean: {}", actorBeanClass.name)
        return emptyArray()
      }

      val neededParams = autoCtor.parameterTypes
          .map { boxPrimitiveClass(it) }
          .toList()

      // We need a modifiable params list where we can remove the params we have found as provided
      // argument so we can inject multiples of the same type.
      val tempNeededParams = neededParams.toMutableList<Class<*>?>()
      val beanParams = neededParams.toMutableList<Class<*>?>()

      // Stuff in the params we need to inject.
      availableArgsClasses.forEachIndexed { _, availCls ->
        var idx = -1
        for ((i, neededParam) in tempNeededParams.withIndex()) {
          if (neededParam != null && neededParam.isAssignableFrom(availCls)) {
            idx = i
            break
          }
        }

        if (idx != -1) {
          beanParams[idx] = null
          // null it out so in case we have multiples of the
          tempNeededParams[idx] = null
        }
      }

      val instancedArgs = beanParams
          .mapIndexedNotNull { i, clazz ->
            if (clazz == null) {
              null
            } else {
              if (autoCtor.parameters[i].isAnnotationPresent(Qualifier::class.java)) {
                val qualifier = autoCtor.parameters[i].getAnnotation(Qualifier::class.java)

                BeanFactoryAnnotationUtils.qualifiedBeanOfType(
                    applicationContext.autowireCapableBeanFactory,
                    clazz,
                    qualifier.value
                )
              } else {
                applicationContext.getBean(clazz)
              }
            }
          }.toMutableList()

      val sortedArgs = ArrayList<Any>()
      val providedArgs = args.toMutableList()
      for (param in neededParams) {
        var wasProvided = false

        for (i in providedArgs.indices) {
          if (boxPrimitiveClass(param).isAssignableFrom(providedArgs[i]!!.javaClass)) {
            sortedArgs.add(providedArgs[i]!!)
            providedArgs.removeAt(i)
            wasProvided = true
            break
          }
        }
        if (wasProvided) {
          continue
        }
        for (i in instancedArgs.indices) {
          val boxedClass = boxPrimitiveClass(param)
          val instancedClass = instancedArgs[i].javaClass
          if (boxedClass.isAssignableFrom(instancedClass)) {
            sortedArgs.add(instancedArgs[i])
            instancedArgs.removeAt(i)
            break
          }
        }
      }

      return sortedArgs.toTypedArray()
    }

  override fun produce(): Actor {
    try {
      return if (args.isEmpty()) {
        applicationContext.getBean(actorBeanClass)
      } else {
        applicationContext.getBean(actorBeanClass, *allCtorArgs)
      }
    } catch (e: Exception) {
      LOG.error { "Could not init actor of type $actorBeanClass" }
      throw e
    }
  }

  private fun boxPrimitiveClass(clazz: Class<*>): Class<*> {
    return when (clazz) {
      Long::class.javaPrimitiveType -> Long::class.java
      Int::class.javaPrimitiveType -> Int::class.java
      Float::class.javaPrimitiveType -> Float::class.java
      Double::class.javaPrimitiveType -> Double::class.java
      else -> clazz
    }
  }

  override fun actorClass(): Class<out Actor> {
    return actorBeanClass
  }
}

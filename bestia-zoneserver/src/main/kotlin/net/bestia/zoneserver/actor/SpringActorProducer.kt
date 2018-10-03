package net.bestia.zoneserver.actor

import akka.actor.Actor
import akka.actor.IndirectActorProducer
import mu.KotlinLogging
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.annotation.Autowired
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
    private val args: ArrayList<*>
) : IndirectActorProducer {

  init {
    if (args.any { it == null }) {
      throw IllegalArgumentException("Arguments can not contain null.")
    }
  }

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

      val availableArgsClasses = args.asSequence().map { it.javaClass }.toSet()
      val autoCtor: Constructor<*>? = getAnnotatedCtor(ctors)
          ?: getZeroArgCtor(ctors)
          ?: getOnlyCtor(ctors)

      if(autoCtor == null) {
          LOG.warn("No Ctor with Autowire annotation found. Can not create bean: {}", actorBeanClass.name)
          return emptyArray()
      }

      val params = autoCtor.parameterTypes
      val neededArgs = params
          .map { boxPrimitiveClass(it) }
          .filter { availableArgsClasses.any { availClass -> it.isAssignableFrom(availClass) } }
          .toSet()

      val instancedArgs = neededArgs.asSequence()
          .map { applicationContext.getBean(it) }
          .toMutableList()

      val sortedArgs = ArrayList<Any>()
      val providedArgs = ArrayList(args)
      for (param in params) {
        var wasProvided = false

        for (i in providedArgs.indices) {
          if (boxPrimitiveClass(param).isAssignableFrom(providedArgs[i].javaClass)) {
            sortedArgs.add(providedArgs[i])
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
    return if (args.size == 0) {
      applicationContext.getBean(actorBeanClass)
    } else {
      try {
        applicationContext.getBean(actorBeanClass, *allCtorArgs)
      } catch (e: ClassNotFoundException) {
        throw BeanCreationException("Class for mixed argument list not found.", e)
      }
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

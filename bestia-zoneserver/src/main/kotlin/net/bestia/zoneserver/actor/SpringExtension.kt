package net.bestia.zoneserver.actor

import akka.actor.*
import mu.KotlinLogging
import org.springframework.context.ApplicationContext

private val LOG = KotlinLogging.logger { }

/**
 * An Akka extension to provide access to the Spring manages Actor Beans.
 *
 * @author Thomas Felix
 */
class SpringExtension private constructor() : AbstractExtensionId<SpringExtension.SpringAkkaExt>() {

  /**
   * Is used by Akka to instantiate the Extension identified by this
   * ExtensionId, internal use only.
   */
  override fun createExtension(system: ExtendedActorSystem): SpringAkkaExt {
    return SpringAkkaExt()
  }

  /**
   * The extension implementation.
   */
  class SpringAkkaExt : Extension {

    @Volatile
    private lateinit var applicationContext: ApplicationContext
    private lateinit var actorProducerClass: Class<out IndirectActorProducer>

    /**
     * Used to initialize the Spring application context for the extension.
     *
     */
    fun initialize(applicationContext: ApplicationContext, actorProducerClass: Class<out IndirectActorProducer>) {
      this.applicationContext = applicationContext
      this.actorProducerClass = actorProducerClass
    }

    /**
     * Create a Props for the specified actorBeanName using the
     * SpringActorProducer class.
     *
     * @return a Props that will create the named actor bean using Spring.
     */
    fun props(actorBeanClass: Class<out Actor>): Props {
      return Props.create(
          actorProducerClass,
          applicationContext,
          actorBeanClass
      ).withDeploy(Deploy.local())
    }

    /**
     * Same as [.props] but inside the args can be additional
     * arguments for the constructor of the [Actor].
     *
     * @param actorBeanClass
     * @param args
     * Additional arguments for the actor ctor.
     * @return A props object containing an application context.
     */
    fun props(actorBeanClass: Class<out AbstractActor>, vararg args: Any): Props {
      return Props.create(
          actorProducerClass,
          applicationContext,
          actorBeanClass,
          args
      ).withDeploy(Deploy.local())
    }
  }

  companion object {
    /**
     * The identifier used to access the SpringExtension.
     */
    private val PROVIDER = SpringExtension()

    private fun getActorName(clazz: Class<out AbstractActor>): String? {
      return try {
        val f = clazz.getField("NAME")
        val t = f.type
        if (t == String::class.java) {
          f.get(null) as String
        } else null

      } catch (e: Exception) {
        null
      }
    }

    fun actorOf(
        actorCtx: ActorRefFactory,
        clazz: Class<out AbstractActor>,
        vararg args: Any
    ): ActorRef {
      return actorOf(actorCtx, clazz, null, *args)
    }

    fun actorOf(
        actorCtx: ActorRefFactory,
        clazz: Class<out AbstractActor>,
        name: String? = null,
        vararg args: Any
    ): ActorRef {
      val props = getSpringProps(actorCtx, clazz, *args)
      val actorName = name ?: getActorName(clazz)
      val actor = if (actorName == null) actorCtx.actorOf(props) else actorCtx.actorOf(props, actorName)
      LOG.debug("Started actor: {}, path: {}", clazz, actor.path())

      return actor
    }

    /**
     * Small helper method to get props via the spring extension (and thus can
     * use dependency injection).
     *
     * @param clazz
     * The Actor class to get the props object for.
     * @param args
     * The arguments are used by spring to fill in additional ctor
     * arguments.
     * @return The created props object.
     */
    fun getSpringProps(system: ActorRefFactory, clazz: Class<out AbstractActor>, vararg args: Any): Props {
      return PROVIDER.get(system.systemImpl()).props(clazz, *args)
    }

    /**
     * Initializes the provider with a new app context which will be used to
     * inject new actors with dependencies.
     *
     * @param system
     * AKKA system.
     * @param appContext
     * The spring app context.
     */
    fun initialize(
        system: ActorSystem,
        appContext: ApplicationContext,
        actorProducerClass: Class<out IndirectActorProducer> = SpringActorProducer::class.java
    ) {
      PROVIDER.get(system).initialize(appContext, actorProducerClass)
    }
  }
}

package net.bestia.zoneserver.actor

import akka.actor.*
import mu.KotlinLogging
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * An Akka extension to provide access to the Spring manages Actor Beans.
 *
 * @author Thomas Felix
 */
@Component
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
  class SpringAkkaExt internal constructor() : Extension {

    @Volatile
    private lateinit var applicationContext: ApplicationContext

    /**
     * Used to initialize the Spring application context for the extension.
     *
     * @param applicationContext
     * The Spring application context.
     */
    fun initialize(applicationContext: ApplicationContext) {
      this.applicationContext = applicationContext
    }

    /**
     * Create a Props for the specified actorBeanName using the
     * SpringActorProducer class.
     *
     * @return a Props that will create the named actor bean using Spring.
     */
    fun props(actorBeanClass: Class<out Actor>): Props {
      return Props.create(
          SpringActorProducer::class.java,
          applicationContext,
          actorBeanClass)
          .withDeploy(Deploy.local())
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
          SpringActorProducer::class.java,
          applicationContext,
          actorBeanClass,
          args)
          .withDeploy(Deploy.local())
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

    fun actorOf(system: ActorSystem, clazz: Class<out AbstractActor>, vararg args: Any): ActorRef {
      val props = if (args.isEmpty()) getSpringProps(system, clazz) else getSpringProps(system, clazz, *args)
      val actorName = getActorName(clazz)
      val actor = if (actorName == null) system.actorOf(props) else system.actorOf(props, actorName)
      LOG.debug("Started actor: {}, path: {}", clazz, actor.path())
      return actor
    }

    /**
     * Creates a new actor via injection of spring dependencies. Does give the
     * actor a custom name by the user and starts actor on the root actor system.
     *
     * @param clazz Default actor name, if the name is null the class name or a
     * random name is used.
     * @return The created [ActorRef].
     */
    fun actorOf(system: ActorSystem, clazz: Class<out AbstractActor>): ActorRef {
      val props = getSpringProps(system, clazz)
      val actorName = getActorName(clazz)
      val actor = if (actorName == null) system.actorOf(props) else system.actorOf(props, actorName)
      LOG.debug("Started actor: {}, path: {}", clazz, actor.path())
      return actor
    }

    /**
     * Like [.createActor] but it will examine the given
     * class if it has a static public string field called NAME and will use
     * this name as actor name. If no such field exists the name "NONAME" will
     * be used.
     *
     * @param clazz
     * The class of the [UntypedActor] to instantiate.
     * @return The created and already registered new actor.
     */
    fun actorOf(ctx: ActorContext, clazz: Class<out AbstractActor>): ActorRef {
      val actorName = getActorName(clazz)

      return actorOf(ctx, clazz, actorName)
    }

    /**
     * Creates a new actor and already register it with this routing actor so it
     * is considered when receiving messages.
     *
     * @param context
     * The [ActorContext] under which the actor should be
     * spawned.
     * @param clazz
     * The class of the [UntypedActor] to instantiate.
     * @param name
     * The name under which the actor should be created. The name can
     * be null then the actor is created with a random name.
     * @return The created and already registered new actor.
     */
    fun actorOf(context: ActorContext, clazz: Class<out AbstractActor>, name: String?): ActorRef {
      val props = getSpringProps(context.system(), clazz)
      val actor = if (name == null) context.actorOf(props) else context.actorOf(props, name)
      LOG.debug("Started actor: {}, path: {}", clazz, actor.path())

      return actor
    }

    /**
     * Returns a new actor with a external name like
     * [.actorOf] but with optional parameter
     * arguments.
     *
     * @param context
     * The [ActorContext] under which the actor should be
     * spawned.
     * @param clazz
     * The class of the [AbstractActor] to be instantiated.
     * @param name
     * The name under which the actor should be created. The name can
     * be null then the actor is created with a random name.
     * @param args
     * The additional arguments delivered to the actor.
     * @return
     */
    fun actorOf(
        context: ActorContext,
        clazz: Class<out AbstractActor>,
        name: String?,
        vararg args: Any
    ): ActorRef {
      val props = getSpringProps(context.system(), clazz, *args)

      return if (name == null) context.actorOf(props) else context.actorOf(props, name)
    }

    fun actorOf(
        context: ActorContext,
        clazz: Class<out AbstractActor>,
        vararg args: Any
    ): ActorRef {
      val actorName = getActorName(clazz)
      return actorOf(context, clazz, actorName, *args)
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
    fun getSpringProps(system: ActorSystem, clazz: Class<out AbstractActor>, vararg args: Any): Props {

      return PROVIDER.get(system).props(clazz, *args)
    }

    /**
     * Small helper method to get props via the spring extension (and thus can
     * use dependency injection).
     *
     * @param clazz
     * The Actor class to get the props object for.
     * @return The created props object.
     */
    private fun getSpringProps(system: ActorSystem, clazz: Class<out AbstractActor>): Props {

      return PROVIDER.get(system).props(clazz)
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
    fun initialize(system: ActorSystem, appContext: ApplicationContext) {
      PROVIDER.get(system).initialize(appContext)
    }
  }
}

package net.bestia.zoneserver.actor

import akka.actor.*
import org.springframework.context.ApplicationContext

/**
 * We need another provider for Spring backed up beans during our tests so we
 * can replace the original with producing mocks during creation.
 * But this class wont use the mocked Spring producer and thus can be used to
 * build functional actor props.
 */
class SpringNoMockExtension private constructor() : AbstractExtensionId<SpringExtension.SpringAkkaExt>() {

  override fun createExtension(system: ExtendedActorSystem): SpringExtension.SpringAkkaExt {
    return SpringExtension.SpringAkkaExt()
  }

  companion object {
    private val PROVIDER = SpringNoMockExtension()

    fun initialize(
        system: ActorSystem,
        appContext: ApplicationContext
    ) {
      PROVIDER.get(system).initialize(appContext, SpringActorProducer::class.java)
    }

    fun getSpringProps(system: ActorRefFactory, clazz: Class<out AbstractActor>, vararg args: Any): Props {
      return PROVIDER.get(system.systemImpl()).props(clazz, *args)
    }
  }
}
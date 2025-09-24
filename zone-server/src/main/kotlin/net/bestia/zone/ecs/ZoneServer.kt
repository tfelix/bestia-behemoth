package net.bestia.zone.ecs

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.EntityTags
import com.github.quillraven.fleks.World
import com.github.quillraven.fleks.configureWorld
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import net.bestia.zone.BestiaException
import net.bestia.zone.ecs.ai.TestAiSystem
import net.bestia.zone.shard.EntityShardRegistry
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.battle.DeathSystem
import net.bestia.zone.ecs.battle.ReceivedDamageSystem
import net.bestia.zone.ecs.message.ECSInMessageProcessor
import net.bestia.zone.ecs.movement.MoveSystem
import net.bestia.zone.ecs.network.DirtyComponentUpdateSystem
import net.bestia.zone.ecs.player.PlayerAOIUpdateSystem
import net.bestia.zone.ecs.persistence.PeriodicSnapshotSystem
import net.bestia.zone.ecs.persistence.PersistAndRemoveSystem
import org.reflections.Reflections
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import kotlin.concurrent.thread

@Service
class ZoneServer(
  private val config: ZoneConfig,
  private val entityRegistry: EntityRegistry,
  private val applicationContext: ApplicationContext,
  // TODO maybe put the entity management into an operations class.
  private val entityShardRegistry: EntityShardRegistry,
  private val entityAOIService: EntityAOIService,
  private val ecsInMessageProcessor: ECSInMessageProcessor,
) {

  @Volatile
  private var isRunning = false

  private var worldThread: Thread? = null

  private var world: World? = null

  @Synchronized
  fun start() {
    if (isRunning) {
      throw BestiaException(
        code = "SERVER_ALREADY_RUNNING",
        message = "World Server is already running"
      )
    }

    LOG.info { "Starting ECS with tick rate: ${config.tickRate} t/s" }
    world = setupWorld()

    startWorldThread()
  }

  /**
   * TODO Must make sure to perform a final save to disc before shutdown.
   */
  @Synchronized
  @PreDestroy
  fun stop() {
    isRunning = false
    worldThread?.join(5000)
  }

  fun addEntity(
    components: List<Component<*>>,
    tags: List<EntityTags> = emptyList(),
  ): EntityId {
    val entity = getWorldOrThrow().entity {
      it += components
      it += tags
    }

    return entityRegistry.getEntityIdOrThrow(entity)
  }

  fun accessWorld(visitor: WorldAcessor) {
    visitor.doWithWorld(getWorldOrThrow())
  }

  private fun setupWorld(): World {
    return configureWorld(entityCapacity = 1000) {
      val injectableBeans = getInjectableBeans()
      injectables {
        injectableBeans.forEach { (beanName, injectableBean) ->
          add(beanName, injectableBean)
        }
      }


      systems {
        val activeSystems = listOf(
          MoveSystem(),
          PeriodicSnapshotSystem(),
          TestAiSystem(),
          PlayerAOIUpdateSystem(),
          ReceivedDamageSystem(),
          DeathSystem(),
          DirtyComponentUpdateSystem(),
          PersistAndRemoveSystem()
        )

        LOG.debug {
          "ECS has the following systems:\n${activeSystems.joinToString("\n") { "- $it" }}"
        }

        activeSystems.forEach { add(it) }
      }

      onAddEntity { entity -> addedEntityHandler(entity) }
      onRemoveEntity { entity -> removeEntityHandler(entity) }
    }
  }

  private fun addedEntityHandler(entity: Entity) {
    LOG.trace { "Entity added: $entity" }

    val entityId = entityRegistry.registerEntity(entity)
    entityShardRegistry.setOwnerToCurrentShard(entityId)
  }

  /**
   * If an entity was removed we need to perform several actions.
   *
   */
  private fun removeEntityHandler(entity: Entity) {
    LOG.trace { "Entity removed: $entity" }

    val entityId = entityRegistry.deleteEntity(entity)
    if (entityId != null) {
      // TODO we could do this here via events and send out entity removed events via spring and
      //   just subscribe all services which need to register on them. Maybe keep the entityRegistry
      //   in here.
      entityShardRegistry.remove(entityId)
      entityAOIService.removeEntityPosition(entityId)
    }
  }

  private fun startWorldThread() {
    isRunning = true
    worldThread = thread(name = "world-server", start = true) {
      val targetFrameTimeMillis = (1000 / config.tickRate).toLong()
      var lastUpdateTime = System.currentTimeMillis()

      try {
        while (isRunning) {
          val updateStartTime = System.currentTimeMillis()
          val deltaTime = (updateStartTime - lastUpdateTime) / 1000f // Convert to seconds
          lastUpdateTime = updateStartTime

          getWorldOrThrow().update(deltaTime) // Process game update
          processEcsMessage()

          val updateDuration = System.currentTimeMillis() - updateStartTime
          val sleepTimeMillis = targetFrameTimeMillis - updateDuration

          if (sleepTimeMillis > 0) {
            Thread.sleep(sleepTimeMillis) // Maintain frame timing
          }
        }
      } catch (e: InterruptedException) {
        LOG.error(e) { }
      } finally {
        getWorldOrThrow().dispose()
      }
    }
  }

  /**
   * TODO we need to limit the message processing time to a certain % max of our frame time.
   * TODO we could also think about dropping this mechanism fully if external processing works fine as well.
   */
  private fun processEcsMessage() {
    while (ecsInMessageProcessor.hasNext()) {
      ecsInMessageProcessor.processMessage(getWorldOrThrow())
    }
  }

  private fun getWorldOrThrow(): World {
    return world ?: throw IllegalStateException("World not set")
  }

  private fun getInjectableBeans(): Map<String, Any> {
    val reflections = Reflections("net.bestia.zone")
    val injectableClasses = reflections.getTypesAnnotatedWith(ZoneInjectable::class.java)

    LOG.info { "Found ECS world injects:\n${injectableClasses.joinToString("\n") { "- ${it.simpleName}" }}" }

    return injectableClasses
      .associate { injectableClass -> injectableClass.simpleName to applicationContext.getBean(injectableClass) }
  }

  companion object {
    private val LOG = KotlinLogging.logger {}
  }
}
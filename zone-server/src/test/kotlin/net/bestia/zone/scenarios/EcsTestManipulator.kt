package net.bestia.zone.scenarios

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import net.bestia.zone.ecs.message.InECSMessageBuffer
import net.bestia.zone.ecs.EntityRegistry
import net.bestia.zone.ecs.message.InECSMessage
import net.bestia.zone.ecs.message.ECSInMessageProcessor
import org.springframework.stereotype.Component

/**
 * This class helps you to manipulate entities at will from the external test code e.g. helpful
 * to access and read, write components.
 */
@Component
class EcsTestManipulator(
  private val ecsMessageBuffer: InECSMessageBuffer,
  private val entityRegistry: EntityRegistry,
) {

  class ExecEcsMessage(
    val entity: Entity,
    val fn: (world: World, entity: Entity) -> Unit
  ) : InECSMessage

  @Component
  class ExecEcsMessageHandler : ECSInMessageProcessor.ECSMessageHandler<ExecEcsMessage> {
    override fun process(
      world: World,
      msg: ExecEcsMessage
    ) {
      msg.fn(world, msg.entity)
    }

    override val handles = ExecEcsMessage::class
  }

  fun executeAsyncOnEntity(
    entityId: Long,
    fn: (world: World, entity: Entity) -> Unit
  ) {
    val entity = entityRegistry.getEntity(entityId)

    requireNotNull(entity) {
      "Entity $entityId not found"
    }

    val msg = ExecEcsMessage( entity, fn)
    ecsMessageBuffer.add(msg)
  }
}
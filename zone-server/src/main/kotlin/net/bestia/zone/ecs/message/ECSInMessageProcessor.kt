package net.bestia.zone.ecs.message

import com.github.quillraven.fleks.World
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * A thread boundary message processor which buffers incoming messages so that they can be consumed inside
 * the ECS thread.
 */
@Component
class ECSInMessageProcessor(
  private val ecsMessageBuffer: InECSMessageBuffer,
  processor: List<ECSMessageHandler<*>>
) {

  private val processorByMessage: Map<KClass<out InECSMessage>, ECSMessageHandler<*>> =
    processor.associateBy { it.handles }

  init {
    val registeredProcessorNames = processorByMessage.map { "${it.key.simpleName} -> ${it.value.javaClass.simpleName}" }
    LOG.info { "Registered message processors:\n${registeredProcessorNames.joinToString(" \n")}" }
  }

  interface ECSMessageHandler<T : InECSMessage> {
    fun process(world: World, msg: T)
    val handles: KClass<T>
  }

  fun hasNext(): Boolean {
    return ecsMessageBuffer.hasNext()
  }

  fun processMessage(world: World) {
    val msg = ecsMessageBuffer.pop()
      ?: return

    val msgHandler = processorByMessage[msg::class]
      ?: throw ECSInNoMessageHandlerException(msg.javaClass)

    @Suppress("UNCHECKED_CAST")
    (msgHandler as ECSMessageHandler<InECSMessage>).process(world, msg)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}


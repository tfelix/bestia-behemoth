package net.bestia.zone.message.processor.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.CMSG
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.ecs.message.InECSMessage
import net.bestia.zone.ecs.message.ECSInMessageProcessor
import net.bestia.zone.ecs.message.InECSMessageBuffer
import org.springframework.beans.factory.annotation.Autowired

abstract class IncomingEcsMessageHandler<T>() :
  InMessageProcessor.IncomingMessageHandler<T>,
  ECSInMessageProcessor.ECSMessageHandler<T>
        where T : CMSG, T : InECSMessage {

  @Autowired
  private lateinit var ecsMessageBuffer: InECSMessageBuffer

  final override fun handle(msg: T): Boolean {
    LOG.trace { "RX: $msg" }
    val isMessageValid = preMessageHandle(msg)

    if (!isMessageValid) {
      // message is dropped
      return true
    } else {
      LOG.debug { "Received ECS msg $msg" }
      ecsMessageBuffer.add(msg)
    }

    return true
  }

  protected open fun preMessageHandle(msg: T): Boolean {
    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
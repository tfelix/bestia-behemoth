package net.bestia.zone.message

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.BestiaException
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * Hands each client message to its handler, behind the sender's earlier messages and on the handler's thread.
 *
 * A [BestiaException] out of a handler is a refusal: the client is told the request is over and keeps its
 * connection. Any other exception is a bug and ends the connection.
 */
@Component
class InMessageProcessor(
  handlers: List<IncomingMessageHandler<*>>,
  private val inbox: AccountTaskExecutor,
  private val outMessageProcessor: OutMessageProcessor,
  metrics: MessageMetrics = MessageMetrics(),
) {

  private val handlerByMessage: Map<KClass<*>, IncomingMessageHandler<*>> = handlers.associateBy { it.handles }

  /** Made up front, so a message looks its meters up in a map and registers nothing. */
  private val metersByMessage: Map<KClass<*>, MessageMetrics.Handler> = handlers.associate { handler ->
    val lane = when (handler) {
      is TickMessageHandler -> MessageMetrics.Lane.TICK
      is IoMessageHandler -> MessageMetrics.Lane.IO
    }

    handler.handles to metrics.forHandler(handler.handles.simpleName ?: "unknown", lane)
  }

  init {
    val shared = handlers.groupBy { it.handles }.filterValues { it.size > 1 }.keys
    require(shared.isEmpty()) { "Message types with more than one handler: ${shared.map { it.simpleName }}" }
  }

  /** Queues [msg] behind the sender's earlier messages and handles it on its handler's thread. */
  fun submit(msg: CMSG): CompletableFuture<Unit> {
    @Suppress("UNCHECKED_CAST")
    val handler = handlerByMessage[msg::class] as IncomingMessageHandler<CMSG>?

    return when (handler) {
      null -> {
        LOG.warn { "No registered message handler for: ${msg::class.java.simpleName}" }
        CompletableFuture.completedFuture(Unit)
      }

      is TickMessageHandler -> inbox.onTick(msg.playerId) { process(handler, msg) { handler.handle(this, msg) } }
      is IoMessageHandler -> inbox.onIo(msg.playerId) { process(handler, msg) { handler.handle(msg) } }
    }
  }

  private fun process(handler: IncomingMessageHandler<*>, msg: CMSG, handle: () -> Boolean) {
    val meters = metersByMessage.getValue(msg::class)
    val started = System.nanoTime()

    try {
      val handled = try {
        handle()
      } catch (e: BestiaException) {
        meters.refused.increment()
        LOG.warn { "Refused ${msg::class.simpleName} from account ${msg.playerId}: ${e.message}" }
        outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(OpError.REQUEST_REFUSED))
        return
      } catch (e: Exception) {
        // A bug: fail the connection rather than leave the client waiting for an answer. errorCode ties this
        // log entry to the generic error the client ends up displaying.
        meters.failed.increment()
        val errorCode = UUID.randomUUID().toString()
        LOG.error(e) {
          "Error during message handling [errorCode=$errorCode] handler=${handler::class.simpleName} " +
            "message=${msg::class.simpleName}"
        }
        throw MessageHandlingFailedException(errorCode, e)
      }

      if (handled) {
        meters.handled.increment()
        LOG.trace { "Message ${msg::class.simpleName} handled by ${handler::class.java.simpleName}" }
      } else {
        meters.unhandled.increment()
        LOG.warn { "${handler::class.java.simpleName} did not handle message ${msg::class.java.simpleName}" }
      }
    } finally {
      meters.duration.record(System.nanoTime() - started, TimeUnit.NANOSECONDS)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

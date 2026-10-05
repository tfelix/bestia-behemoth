package net.bestia.zone.message

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

/** Hands each client message to its handler, behind the sender's earlier messages and on the handler's thread. */
@Component
class InMessageProcessor(
  handlers: List<IncomingMessageHandler<*>>,
  private val inbox: AccountTaskExecutor,
) {

  private val handlerByMessage: Map<KClass<*>, IncomingMessageHandler<*>> = handlers.associateBy { it.handles }

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
    val handled = try {
      handle()
    } catch (e: Exception) {
      // A handler exception is a bug, not a "this client request was invalid" outcome - it
      // leaves the client waiting for a response that will never come. Fail the connection
      // instead of swallowing it: errorCode ties this log entry to whatever generic error the
      // client ends up displaying.
      val errorCode = UUID.randomUUID().toString()
      LOG.error(e) {
        "Error during message handling [errorCode=$errorCode] handler=${handler::class.simpleName} " +
          "message=${msg::class.simpleName}"
      }
      throw MessageHandlingFailedException(errorCode, e)
    }

    if (handled) {
      LOG.trace { "Message ${msg::class.simpleName} handled by ${handler::class.java.simpleName}" }
    } else {
      LOG.warn { "${handler::class.java.simpleName} did not handle message ${msg::class.java.simpleName}" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

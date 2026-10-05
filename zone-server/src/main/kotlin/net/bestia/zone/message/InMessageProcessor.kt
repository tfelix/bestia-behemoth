package net.bestia.zone.message

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

/**
 * Processes incoming messages.
 * There are several types of messages. Some might require a round trip/query into the ECS because the real time
 * information is only stored there, others might be directly handled from the database. For example queries of the
 * map information is directly in the database and not inside the ECS.
 * Some messages are just translated into a component update, send to ECS and don't yield a direct answer.
 *
 *  - RequestMessages: which can be directly handled via a IncomingMessageHandler and are translated into an immediate
 *  answer to the client. For example request to the inventory which is always in sync with the DB or a chat message
 *  which does not go into the ECS.
 *
 *  - ComponentMessages: are translated into a component update and send into the ECS to get further processed there.
 */
@Component
class InMessageProcessor(
  handler: List<IncomingMessageHandler<*>>,
  private val inbox: AccountTaskExecutor,
) {

  private val existingHandler = handler.groupBy { it.handles }

  private val laneByMessage: Map<KClass<*>, HandlerLane> = existingHandler.mapValues { (type, handlers) ->
    val lanes = handlers.map { it.lane }.toSet()
    require(lanes.size == 1) { "Handlers of ${type.simpleName} disagree about their lane: $lanes" }
    lanes.single()
  }

  interface IncomingMessageHandler<T : CMSG> {
    val handles: KClass<T>

    /** Where [handle] runs. A handler that touches the database must say [HandlerLane.IO]. */
    val lane: HandlerLane
      get() {
        return HandlerLane.TICK
      }

    /**
     * return: Signals if the message was successfully handled.
     */
    fun handle(msg: T): Boolean
  }

  /** Queues [msg] behind the sender's earlier messages and handles it on its lane. */
  fun submit(msg: CMSG): CompletableFuture<Unit> {
    val lane = laneByMessage[msg::class] ?: HandlerLane.TICK

    return inbox.execute(msg.playerId, lane) { process(msg) }
  }

  fun <T : CMSG> process(msg: T) {
    val possibleHandler = existingHandler[msg::class] ?: emptyList()

    if (possibleHandler.isEmpty()) {
      LOG.warn { "No registered message handler for: ${msg::class.java.simpleName}" }
      return
    }

    @Suppress("UNCHECKED_CAST")
    val wasMsgHandled = possibleHandler.any { handler ->
      try {
        val isHandled = (handler as IncomingMessageHandler<T>).handle(msg)

        if (isHandled) {
          LOG.trace { "Message ${msg::class.simpleName} handled by ${handler::class.java.simpleName}" }
        }

        isHandled
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
    }

    if (!wasMsgHandled) {
      LOG.warn {
        "No message handler from the possible handler: " +
                "${possibleHandler.map { it::class.java.simpleName }} handled message ${msg::class.java.simpleName}"
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
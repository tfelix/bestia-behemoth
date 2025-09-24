package net.bestia.zone.ecs.message

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * A thread boundary message processor which buffers outgoing messages so that they can be processed outside
 * the ECS thread. This is helpful to offload more complex processing outside the ECS system.
 * Uses a single thread executor to process messages asynchronously.
 */
@Component
class ECSOutMessageProcessor(
  private val outEcsMessageBuffer: OutECSMessageBuffer,
  processor: List<OutECSMessageHandler<*>>
) {
  interface OutECSMessageHandler<T : OutECSMessage> {
    fun process(msg: T)
    val handles: KClass<T>
  }

  private val processorByMessage: Map<KClass<out OutECSMessage>, OutECSMessageHandler<*>> =
    processor.associateBy { it.handles }

  private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
    Thread(runnable, "ECS-OutMessage-Processor").apply {
      isDaemon = true
    }
  }

  @Volatile
  private var isProcessing = false

  init {
    LOG.info {
      val registeredProcessorNames =
        processorByMessage.map { "- ${it.key.simpleName} -> ${it.value.javaClass.simpleName}" }
      "Registered ECS out message processors:\n${registeredProcessorNames.joinToString("\n")}"
    }

    // Start the background processing task if there are registered handler.
    if (processorByMessage.isNotEmpty()) {
      isProcessing = true
      startBackgroundProcessing()
    }
  }

  /**
   * Shutdown the executor service gracefully.
   */
  @PreDestroy
  fun shutdown() {
    LOG.info { "Shutting down ECS out message processor" }

    executor.shutdown()

    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow()
        LOG.warn { "ECS out message processor did not terminate gracefully, forced shutdown" }
      }
    } catch (e: InterruptedException) {
      executor.shutdownNow()
      Thread.currentThread().interrupt()
    }
  }

  /**
   * Starts background processing of messages using the single thread executor.
   */
  private fun startBackgroundProcessing() {
    executor.scheduleWithFixedDelay({
      if (isProcessing) {
        try {
          while (outEcsMessageBuffer.hasNext()) {
            processNextMessage()
          }
        } catch (e: Exception) {
          LOG.error(e) { "Error processing ECS out messages" }
        } finally {
          isProcessing = false
        }
      }
    }, 0, 1, TimeUnit.MILLISECONDS)
  }

  /**
   * Processes the next available message from the buffer.
   */
  private fun processNextMessage() {
    val msg = outEcsMessageBuffer.pop()
      ?: return

    val msgHandler = processorByMessage[msg::class]
      ?: throw ECSOutNoMessageHandlerException(msg.javaClass)

    @Suppress("UNCHECKED_CAST")
    (msgHandler as OutECSMessageHandler<OutECSMessage>).process(msg)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
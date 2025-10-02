package net.bestia.zone.ecs2

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.BestiaException
import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.EntityManager
import net.bestia.zone.ecs.IteratingSystem
import net.bestia.zone.ecs.NoReadLockForEntityException
import net.bestia.zone.ecs.OnEntityRemovedListener
import net.bestia.zone.ecs.PeriodicSystem
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ecs.ZoneOperations
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.concurrent.*

@Service
class ZoneServer(
  private val config: ZoneConfig,
  private val iteratingSystems: List<IteratingSystem>,
  private val periodicSystems: List<PeriodicSystem>,
  private val onEntityRemovedListener: List<OnEntityRemovedListener>
) : ZoneOperations {
  private val ecsThreadPool = Executors.newFixedThreadPool(1)
  private val externalThreadPool = Executors.newFixedThreadPool(2)
  private val scheduledExecutor = Executors.newScheduledThreadPool(2)
  private val entityManager = EntityManager()
  private val removeEntityJobQueue = LinkedBlockingQueue<Runnable>()

  @Volatile
  private var running = false
  private var lastTickTime = System.currentTimeMillis()

  init {
    LOG.info {
      "ZoneServer loaded the following systems:\n" +
              "Iterating Systems:\n" +
              iteratingSystems.joinToString("\n") { " - ${it.javaClass.simpleName}" } +
              "\nPeriodic Systems:\n" +
              periodicSystems.joinToString("\n") { " - ${it.javaClass.simpleName}" }
    }
  }

  fun start() {
    if (running) {
      throw BestiaException(
        code = "SERVER_ALREADY_RUNNING",
        message = "World Server is already running"
      )
    }
    running = true

    // Start main ECS loop
    ecsThreadPool.submit {
      LOG.info { "Zone ECS system started" }

      while (running) {
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastTickTime) / 1000.0f
        lastTickTime = currentTime

        try {
          // Process queued jobs, these run inside the ECS context. Maybe this is not even required and
          // they could just run in parallel.
          while (removeEntityJobQueue.isNotEmpty()) {
            val job = removeEntityJobQueue.poll()
            job?.run()
          }

          // Process iterating systems (run every tick)
          iteratingSystems.forEach { system ->
            val allEntities = entityManager.getAllEntities()

            allEntities.forEach { entity ->
              // Re-check if entity still matches after potential component changes
              if (system.entityMatches(entity)) {
                withEntityWriteLock(entity.id) {
                  system.update(deltaTime, it, this)
                }
              }
            }
          }

          // Process periodic systems (run only when their delay has passed)
          periodicSystems.forEach { system ->
            if (system.shouldExecute(deltaTime)) {
              val allEntities = entityManager.getAllEntities()

              allEntities.forEach { entity ->
                // Re-check if entity still matches after potential component changes
                if (system.entityMatches(entity)) {
                  withEntityWriteLock(entity.id) {
                    system.update(deltaTime, it, this)
                  }
                }
              }
            }
          }

        } catch (e: Exception) {
          LOG.error(e) { "Error in ECS tick: ${e.message}" }
        }

        // Sleep to maintain tick rate
        val sleepTime = (1000 / config.tickRate) - (System.currentTimeMillis() - currentTime)
        if (sleepTime > 0) {
          Thread.sleep(sleepTime)
        }
      }
    }
  }

  fun stop() {
    running = false
    externalThreadPool.shutdown()
    scheduledExecutor.shutdown()
    ecsThreadPool.shutdown()

    try {
      if (!externalThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
        externalThreadPool.shutdownNow()
      }
      if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduledExecutor.shutdownNow()
      }
      if (!ecsThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
        ecsThreadPool.shutdownNow()
      }
    } catch (_: InterruptedException) {
      externalThreadPool.shutdownNow()
      scheduledExecutor.shutdownNow()
      ecsThreadPool.shutdownNow()
    }
  }

  /*
  override fun scheduleJob(delaySeconds: Long, action: (ZoneOperations) -> Unit) {
    scheduledExecutor.schedule({
      action(this)
    }, delaySeconds, TimeUnit.SECONDS)
  }*/

  fun scheduleRepeatingJob(initialDelaySeconds: Long, periodSeconds: Long, action: (ZoneServer) -> Unit) {
    scheduledExecutor.scheduleAtFixedRate({
      action(this)
    }, initialDelaySeconds, periodSeconds, TimeUnit.SECONDS)
  }

  override fun queueExternalJob(action: () -> Unit) {
    externalThreadPool.submit(action)
  }

  // Queue a job with a future result - executes in background thread pool
  fun <T> queueJobWithFuture(action: (ZoneServer) -> T): CompletableFuture<T> {
    return CompletableFuture.supplyAsync({
      action(this@ZoneServer)
    }, externalThreadPool)
  }

  // Schedule a job with future result to run after a delay - executes in background
  fun <T> scheduleJobWithFuture(delaySeconds: Long, action: (ZoneServer) -> T): CompletableFuture<T> {
    return CompletableFuture.supplyAsync({
      // Wait for the delay, then execute
      Thread.sleep(delaySeconds * 1000)
      action(this@ZoneServer)
    }, scheduledExecutor)
  }

  // Convenience methods for common operations
  override fun addEntity(): EntityId = entityManager.createEntity().id

  override fun removeEntity(entityId: EntityId) {
    removeEntityJobQueue.offer {
      entityManager.removeEntity(entityId)
      onEntityRemovedListener.forEach { it.onEntityRemoved(entityId) }
    }
  }

  override fun hasEntity(entityId: EntityId): Boolean {
    return entityManager.hasEntity(entityId)
  }

  // TODO Maybe return Unit as you should only write here?
  override fun <T> addEntityWithWriteLock(action: (Entity) -> T): EntityId {
    val entityId = addEntity()

    withEntityWriteLockOrThrow(entityId, action)

    return entityId
  }

  override fun <T> withEntityReadLock(entityId: Long, action: (Entity) -> T): T? {
    return entityManager.withEntityReadLock(entityId, action)
  }

  override fun <T> withEntityReadLockOrThrow(entityId: Long, action: (Entity) -> T): T {
    return entityManager.withEntityReadLock(entityId, action)
      ?: throw NoReadLockForEntityException(entityId)
  }

  override fun <T> withEntityWriteLock(entityId: Long, action: (Entity) -> T): T? {
    return entityManager.withEntityWriteLock(entityId, action)
  }

  private fun <T> withEntityWriteLockOrThrow(entityId: Long, action: (Entity) -> T): T {
    return entityManager.withEntityWriteLock(entityId, action)
      ?: throw NoReadLockForEntityException(entityId)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

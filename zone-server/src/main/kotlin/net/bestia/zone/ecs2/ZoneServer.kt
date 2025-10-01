package net.bestia.zone.ecs2

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.BestiaException
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.concurrent.*
import net.bestia.zone.ecs2.IteratingSystem as ECSSystem


@Service
class ZoneServer(
  private val config: ZoneConfig,
  private val systems: List<ECSSystem>
) {
  private val ecsThreadPool = Executors.newFixedThreadPool(1)
  private val externalThreadPool = Executors.newFixedThreadPool(2)
  private val scheduledExecutor = Executors.newScheduledThreadPool(2)
  private val entityManager = EntityManager()
  private val jobQueue = LinkedBlockingQueue<Runnable>()

  @Volatile
  private var running = false
  private var lastTickTime = System.currentTimeMillis()

  init {
    LOG.info {
      "ZoneServer loaded the following systems:\n" +
              systems.joinToString("\n") { " - ${it.javaClass.simpleName}" }
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
      while (running) {
        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastTickTime
        lastTickTime = currentTime

        try {
          // Process queued jobs, these run inside the ECS context. Maybe this is not even required and
          // they could just run in parallel.
          while (jobQueue.isNotEmpty()) {
            val job = jobQueue.poll()
            job?.run()
          }

          // Process each system with entity-by-entity updates, this can be improved for sure to give
          // quicker access to entities in the system.
          systems.forEach { system ->
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

  fun queueJob(action: (ZoneServer) -> Unit) {
    jobQueue.offer {
      action(this@ZoneServer)
    }
  }

  fun scheduleJob(delaySeconds: Long, action: (ZoneServer) -> Unit) {
    scheduledExecutor.schedule({
      action(this)
    }, delaySeconds, TimeUnit.SECONDS)
  }

  fun scheduleRepeatingJob(initialDelaySeconds: Long, periodSeconds: Long, action: (ZoneServer) -> Unit) {
    scheduledExecutor.scheduleAtFixedRate({
      action(this)
    }, initialDelaySeconds, periodSeconds, TimeUnit.SECONDS)
  }

  fun queueExternalJob(action: () -> Unit) {
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
  fun addEntity(): EntityId = entityManager.createEntity().id

  fun removeEntity(entityId: EntityId) {
    queueJob { entityManager.removeEntity(entityId) }
  }

  fun removeEntity(entity: Entity) {
    removeEntity(entity.id)
  }

  fun hasEntity(entityId: EntityId): Boolean {
    return entityManager.hasEntity(entityId)
  }

  // TODO Maybe return Unit as you should only write here?
  fun <T> addEntityWithWriteLock(action: (Entity) -> T): EntityId {
    val entityId = addEntity()

    withEntityWriteLockOrThrow(entityId, action)

    return entityId
  }

  fun <T> withEntityReadLock(entityId: Long, action: (Entity) -> T): T? {
    return entityManager.withEntityReadLock(entityId, action)
  }

  fun <T> withEntityReadLockOrThrow(entityId: Long, action: (Entity) -> T): T {
    return entityManager.withEntityReadLock(entityId, action)
      ?: throw NoReadLockForEntity(entityId)
  }

  fun <T> withEntityWriteLock(entityId: Long, action: (Entity) -> T): T? {
    return entityManager.withEntityWriteLock(entityId, action)
  }

  fun <T> withEntityWriteLockOrThrow(entityId: Long, action: (Entity) -> T): T {
    return entityManager.withEntityWriteLock(entityId, action)
      ?: throw NoReadLockForEntity(entityId)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}



package net.bestia.zoneserver.script.api

import akka.actor.ActorRefFactory
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.Entity
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Does a blocking lookup of entities. This should only be used in conjunction with scripts
 * as its unavoidable that scripts need an entity in order to perform certain computations.
 */
@Service
class BlockingEntityFetchService(
    val msgApi: MessageApi,
    val ctx: ActorRefFactory
) {

  fun getEntity(entityId: Long): Entity? {
    val entityFuture = CompletableFuture<Entity>()
    awaitEntityResponse(
        messageApi = msgApi,
        ctx = ctx,
        entityId = entityId
    ) {
      entityFuture.complete(it)
    }

    return try {
      entityFuture.get(ENTITY_RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    } catch(e: TimeoutException) {
      // timeout
      null
    }
  }

  companion object {
    private const val ENTITY_RESPONSE_TIMEOUT_MS = 1000L
  }
}
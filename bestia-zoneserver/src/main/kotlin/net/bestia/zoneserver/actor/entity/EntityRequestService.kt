package net.bestia.zoneserver.actor.entity

import akka.actor.ActorSystem
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.Entity
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Service
class EntityRequestService(
    private val messageApi: MessageApi,
    private val actorSystem: ActorSystem
) {

  fun requestEntity(entityId: Long): Entity {
    val futureEntity = CompletableFuture<Entity>()

    awaitEntityResponse(messageApi, actorSystem, entityId) {
      futureEntity.complete(it)
    }

    return futureEntity.get(300, TimeUnit.MILLISECONDS)
  }
}
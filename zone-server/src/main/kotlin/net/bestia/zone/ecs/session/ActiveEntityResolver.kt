package net.bestia.zone.ecs.session

import com.github.quillraven.fleks.Entity
import net.bestia.zone.account.NoActiveEntityException
import net.bestia.zone.util.AccountId
import net.bestia.zone.ecs.EntityRegistry
import org.springframework.stereotype.Component

@Component
class ActiveEntityResolver(
  private val connectionInfoService: ConnectionInfoService,
  private val entityRegistry: EntityRegistry
) {

  fun findActiveEntityByAccountId(accountId: AccountId): Entity? {
    try {
      val activeEntityId = connectionInfoService.getActiveEntityId(accountId)

      return entityRegistry.getEntity(activeEntityId)
    } catch (_: NoActiveSessionException) {
      return null
    }
  }

  fun findActiveEntityByAccountIdOrThrow(accountId: AccountId): Entity {
    return findActiveEntityByAccountId(accountId)
      ?: throw NoActiveEntityException()
  }
}
package net.bestia.zone.ecs.session

import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

class EntityNotOwnedSessionException(
  accountId: AccountId,
  selectedEntityId: EntityId
) : SessionException(
  code = "SESSION_ENTITY_NOT_OWNED",
  message = "Account $accountId does not own any entity $selectedEntityId"
)


package net.bestia.zone.ecs.session

import net.bestia.zone.util.AccountId

class NoActiveSessionException(accountId: AccountId): SessionException(
  code = "SESSION_NO_ACTIVE",
  message = "Account $accountId has no active session"
)
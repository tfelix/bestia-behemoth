package net.bestia.zone.identity.ecs

import net.bestia.zone.ecs.core.Component

data class Account(
  var accountId: Long,
) : Component

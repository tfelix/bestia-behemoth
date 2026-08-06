package net.bestia.zone.ecs.account

import net.bestia.zone.ecs.core.Component

data class Master(
  var masterId: Long,
  /**
   * The master's display name, carried on the entity so anything running on the tick thread can address
   * the player by name without a repository lookup. Defaulted for entities built in tests that only care
   * about the marker aspect of this component.
   */
  var name: String = "",
) : Component
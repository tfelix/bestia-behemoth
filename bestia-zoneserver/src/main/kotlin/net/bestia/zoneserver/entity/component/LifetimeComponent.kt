package net.bestia.zoneserver.entity.component

import java.time.Instant

/**
 * Lifetime is used to control the time to live for an entity.
 * When the instant is reached the entity is killed.
 * @author Thomas Felix
 */
data class LifetimeComponent(
    override val entityId: Long,
    val killOn: Instant
) : Component {
  init {
    require(killOn > Instant.now()) { "Kill instant must be in the future" }
  }
}

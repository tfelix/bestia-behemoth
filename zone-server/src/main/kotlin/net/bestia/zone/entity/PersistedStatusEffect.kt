package net.bestia.zone.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * One durable status effect instance, keyed by the ECS entity id that carries it. The relational
 * mirror of a single [net.bestia.zone.ecs.battle.effects.ActiveStatusEffect].
 *
 * Deliberately keyed by entity id rather than by master/mob, and deliberately its own table rather
 * than a blob inside [PersistedEntity]:
 * - masters have no [PersistedEntity] row at all (they are written to the relational `master` table
 *   by `MasterEntityPersister`), so a blob there would not reach them;
 * - `Float.POSITIVE_INFINITY` does not round-trip through JSON, while [remainingSeconds] being
 *   nullable states "never expires" outright;
 * - a row can be written for an entity id that has not been spawned yet, which is what lets
 *   [net.bestia.zone.account.master.MasterFactory] seed an effect at master creation time.
 */
@Entity
@Table(
  name = "status_effect",
  indexes = [Index(name = "idx_status_effect_owner", columnList = "owner_entity_id")]
)
class PersistedStatusEffect(
  /** The ECS entity id the effect belongs to. Not a foreign key - the entity need not exist yet. */
  @Column(name = "owner_entity_id", nullable = false)
  var ownerEntityId: Long = 0,

  /** [net.bestia.zone.battle.status.StatusEffectDefinition.id] of the effect. */
  @Column(name = "definition_id", nullable = false)
  var definitionId: Long = 0,

  @Column(name = "level", nullable = false)
  var level: Int = 1,

  /**
   * Seconds left when the row was written, or null for an effect that never expires. Offline time
   * does not count against it: the value is replayed verbatim on the next spawn.
   */
  @Column(name = "remaining_seconds", nullable = true)
  var remainingSeconds: Float? = null,

  /** Entity that caused the effect, if it is still meaningful to attribute it. */
  @Column(name = "source_entity_id", nullable = true)
  var sourceEntityId: Long? = null,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}

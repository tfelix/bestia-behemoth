package net.bestia.zoneserver.entity.component

import net.bestia.zoneserver.entity.EntityId
import java.time.Duration
import java.time.Instant

/**
 * Contains data regarding a battle. This is important to distribute EXP for
 * example after a battle has taken place. It also keeps track which entity is
 * currently being attacked by this entity.
 *
 * @author Thomas Felix
 */
data class BattleDamageComponent(
    override val entityId: Long,
    val damageDealers: List<DamageEntry> = emptyList()
) : Component {

  data class DamageEntry(
      val entityId: Long,
      val time: Instant,
      val damage: Long
  )

  fun removeOutdatedEntries(): BattleDamageComponent {
    val now = Instant.now()
    return copy(
        damageDealers = damageDealers.filter { Duration.between(it.time, now) < DAMAGE_RETAIN_TIME }
    )
  }

  private fun sortDamages() {
    damageDealers.sortedBy { it.damage }
  }

  /**
   * @return The percentage damage distribution done by all entities. The map
   * is immutable.
   */
  /*
  val damageDistribution: Map<Long, Double>
    get() {
      val totalDmg = damageDealers.values.sumByDouble { it.damage.toDouble() }
      return damageDealers.map { (k, v) -> k to v.damage / totalDmg }.toMap()
    }*/

  fun addDamageReceived(damageDealer: EntityId, damage: Long): BattleDamageComponent {
    if (damage <= 0) {
      return this
    }

    val now = Instant.now()
    val newDmg = damageDealers.firstOrNull { it.entityId == damageDealer }?.let {
      it.copy(time = now, damage = it.damage + damage)
    } ?: DamageEntry(entityId, now, damage)

    val newDamages = (damageDealers + newDmg).take(ENTITY_DAMAGE_TRACK_COUNT)

    val newBattleDamage = copy(damageDealers = newDamages)
    newBattleDamage.sortDamages()

    return newBattleDamage
  }

  companion object {
    /**
     * Number of entities whose damage is tracked against the entity.
     */
    private const val ENTITY_DAMAGE_TRACK_COUNT = 20
    private val DAMAGE_RETAIN_TIME = Duration.ofMinutes(30)
  }
}

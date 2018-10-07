package net.bestia.zoneserver.entity.component

/**
 * Contains data regarding a battle. This is important to distribute EXP for
 * example after a battle has taken place. It also keeps track which entity is
 * currently being attacked by this entity.
 *
 * @author Thomas Felix
 */
data class BattleComponent(
    override val entityId: Long
) : Component {

  data class DamageEntry(var time: Long, var damage: Long)

  val damageDealers = mutableMapOf<Long, DamageEntry>()

  /**
   * @return The percentage damage distribution done by all entities. The map
   * is immutable.
   */
  val damageDistribution: Map<Long, Double>
    get() {
      val totalDmg = damageDealers.values.map { it.damage }.sum().toDouble()
      return damageDealers.entries
          .asSequence()
          .map { (key, value) -> key to value.damage / totalDmg }
          .sortedBy { it.second }
          .toList().toMap()
    }

  fun addDamageReceived(entityId: Long, damage: Int) {
    if (damage <= 0) {
      return
    }

    val curTime = System.currentTimeMillis()

    val newDamageReceived = damageDealers[entityId]?.also {
      it.damage += damage
    } ?: DamageEntry(curTime, damage.toLong())

    damageDealers[entityId] = newDamageReceived
    if (damageDealers.size > ENTITY_DAMAGE_TRACK_COUNT) {
      damageDealers.map { it.key to it.value }.sortedBy { it.second.damage }
          .takeLast(damageDealers.size - ENTITY_DAMAGE_TRACK_COUNT)
          .forEach { damageDealers.remove(it.first) }
    }
  }

  companion object {
    /**
     * Number of entities whose damage is tracked against the entity.
     */
    private const val ENTITY_DAMAGE_TRACK_COUNT = 20
  }
}

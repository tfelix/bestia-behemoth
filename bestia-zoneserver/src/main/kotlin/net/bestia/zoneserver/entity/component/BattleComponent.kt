package net.bestia.zoneserver.entity.component

/**
 * Contains data regarding a battle. This is important to distribute EXP for
 * example after a battle has taken place. It also keeps track which entity is
 * currently being attacked by this entity.
 *
 * @author Thomas Felix
 */
data class BattleComponent(
    override val id: Long,
    override val entityId: Long
) : Component {

  private val damageReceived = mutableMapOf<Long, DamageEntry>()

  /**
   * @return The percentage damage distribution done by all entities. The map
   * is immutable.
   */
  val damageDistribution: Map<Long, Double>
    get() {
      val totalDmg = damageReceived.values.map { it.damage }.sum().toDouble()
      return damageReceived.entries
          .asSequence()
          .map { (key, value) -> key to value.damage / totalDmg }
          .sortedBy { it.second }
          .toList().toMap()
    }

  /**
   * Returns a set of all damage dealers who took part into damaging this
   * entity. The set is immutable.
   *
   * @return
   */
  val damageDealers: Set<Long>
    get() = damageReceived.keys.toSet()

  private class DamageEntry(var time: Long, var damage: Long)

  /**
   * Removes all damage entries which are older then
   * [DAMAGE_ENTRY_REMOVE_DELAY_MS].
   */
  fun clearOldDamageEntries() {
    val curTime = System.currentTimeMillis()
    damageReceived.entries.removeIf { x -> x.value.time + DAMAGE_ENTRY_REMOVE_DELAY_MS < curTime }
  }

  fun clearDamageEntries() {
    damageReceived.clear()
  }

  fun addDamageReceived(entityId: Long, damage: Int) {
    if (damage <= 0) {
      return
    }

    val curTime = System.currentTimeMillis()

    val newDamageReceived = damageReceived[entityId]?.also {
      it.damage += damage
    } ?: DamageEntry(curTime, damage.toLong())

    damageReceived[entityId] = newDamageReceived
    if (damageReceived.size > ENTITY_DAMAGE_TRACK_COUNT) {
      damageReceived.map { it.key to it.value }.sortedBy { it.second.damage }
          .takeLast(damageReceived.size - ENTITY_DAMAGE_TRACK_COUNT)
          .forEach { damageReceived.remove(it.first) }
    }
  }

  companion object {
    /**
     * Number of entities whose damage is tracked against the entity.
     */
    private const val ENTITY_DAMAGE_TRACK_COUNT = 20

    /**
     * Time delay until a received damage is beeing removed.
     */
    private const val DAMAGE_ENTRY_REMOVE_DELAY_MS = 30 * 60 * 1000 // 30min
  }
}

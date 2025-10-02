package net.bestia.zone.ecs.battle

import net.bestia.zone.ecs.Component
import net.bestia.zone.util.EntityId
import java.time.Duration

class TakenDamage(): Component {

  private data class DamageEntry(
    var damage: Int,
    var damageTakenAt: Long
  )

  private val value: MutableMap<EntityId, DamageEntry> = mutableMapOf()

  fun damagePercentages(): Map<EntityId, Float> {
    val total = totalDamage

    return if (totalDamage == 0) {
      value.mapValues { 0f }
    } else {
      value.mapValues { it.value.damage.toFloat() / total }
    }
  }

  fun addDamage(entity: EntityId, damage: Int) {
    val currentTime = System.currentTimeMillis()

    // Add or update damage and timestamp
    val existingEntry = value[entity]
    if (existingEntry != null) {
      existingEntry.damage += damage
      existingEntry.damageTakenAt = currentTime
    } else {
      value[entity] = DamageEntry(damage, currentTime)
    }

    // If more than 10 entries, remove the one with the least damage
    if (value.size > MAX_DAMAGE_ENTRIES) {
      val minEntry = value.minByOrNull { it.value.damage }
      if (minEntry != null) {
        value.remove(minEntry.key)
      }
    }
  }

  fun removeOldEntries() {
    val currentTime = System.currentTimeMillis()
    val cutoffTime = currentTime - MAX_DAMAGE_RETAIN_TIME_MS

    value.entries.removeIf { it.value.damageTakenAt < cutoffTime }
  }

  private val totalDamage get() = value.values.sumOf { it.damage }

  companion object {
    private const val MAX_DAMAGE_ENTRIES = 10
    private val MAX_DAMAGE_RETAIN_TIME_MS = Duration.ofMinutes(5).toMillis()
  }
}

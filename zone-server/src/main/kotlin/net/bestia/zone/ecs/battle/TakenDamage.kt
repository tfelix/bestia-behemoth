package net.bestia.zone.ecs.battle

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import com.github.quillraven.fleks.Entity

class TakenDamage() : Component<TakenDamage> {

  private val value: MutableMap<Entity, Int> = mutableMapOf()

  fun damagePercentages(): Map<Entity, Float> {
    val total = totalDamage

    return if (totalDamage == 0) {
      value.mapValues { 0f }
    } else {
      value.mapValues { it.value.toFloat() / total }
    }
  }

  fun addDamage(entity: Entity, damage: Int) {
    // Add or update damage
    value[entity] = (value[entity] ?: 0) + damage
    // If more than 10 entries, remove the one with the least damage
    if (value.size > MAX_DAMAGE_ENTRIES) {
      val minEntry = value.minByOrNull { it.value }
      if (minEntry != null) {
        value.remove(minEntry.key)
      }
    }
  }

  private val totalDamage get() = value.values.sum()

  override fun type() = TakenDamage

  companion object : ComponentType<TakenDamage>() {
    private const val MAX_DAMAGE_ENTRIES = 10
  }
}

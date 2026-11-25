package net.bestia.zone.world.mana

import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component

/** Everything that releases mana into the field goes through here, so the amounts stay in one place. Tick only. */
@Component
class ManaEmission(
  private val field: ManaField,
  private val config: ManaEmissionConfig,
) {

  fun onCast(at: Vec3L, manaCost: Int) {
    if (manaCost <= 0) return
    release(at, manaCost * config.motesPerManaPoint)
  }

  fun onKill(at: Vec3L, level: Int, boss: Boolean) {
    val factor = if (boss) config.bossFactor else 1.0
    release(at, level * config.motesPerKillLevel * factor)
  }

  private fun release(at: Vec3L, motes: Double) {
    field.add(at.x.toDouble(), at.y.toDouble(), motes)
  }
}

package net.bestia.zone.battle.ecs.effects

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test

class AreaMechanicsApplierTest {

  private val statusEffects = mockk<StatusEffectService>(relaxed = true)
  private val world = mockk<World>(relaxed = true)
  private val sut = AreaMechanicsApplier(statusEffects, mockk(relaxed = true))

  @Test
  fun `a zone status for everyone lands once on whoever stands inside, foe or friend`() {
    val still = area(AreaMechanics(zoneStatus = StatusEffectId.SHELTERED, zoneStatusForEveryone = true))

    for (id in listOf(FOE, ALLY)) sut.onAnyone(world, id, still)
    sut.onFoe(world, FOE, still, Vec3L(0, 0, 0))
    sut.onAlly(world, ALLY, still)

    verify(exactly = 1) { statusEffects.applyEffect(world, FOE, StatusEffectId.SHELTERED, 1, CASTER) }
    verify(exactly = 1) { statusEffects.applyEffect(world, ALLY, StatusEffectId.SHELTERED, 1, CASTER) }
  }

  private fun area(mechanics: AreaMechanics) = AreaEffect(
    casterId = CASTER, skillId = 84, skillLevel = 1, radiusTiles = 1,
    damagePerTick = 0, tickIntervalSeconds = 0.5f, remainingTicks = 40, mechanics = mechanics
  )

  private companion object {
    const val CASTER = 1L
    const val FOE = 2L
    const val ALLY = 3L
  }
}

package net.bestia.zone.scenarios

import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ward end to end: boot raises a stone at the home spawn point, its aura pulses on the real tick, and the
 * fixture players, who arrived at that spawn point, are warded against each other.
 */
class WardStoneScenario : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Test
  @Order(1)
  fun `a ward stone carrying its aura stands at every home spawn point`() {
    val stones = world.read { wardStones(this) }

    assertTrue(stones.isNotEmpty(), "boot raised no ward stone")
    assertTrue(stones.all { world.read { carriesAura(this, it) } })
  }

  @Test
  @Order(2)
  fun `two players who arrived there cannot harm each other`() {
    val first = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)
    val second = connectionInfoService.getSelectedMasterEntityId(clientPlayer2.connectedPlayerId)

    await {
      assertEquals(DamageGate.Verdict.WARDED, world.read { DamageGate.verdict(this, first, second) })
    }
  }

  private fun wardStones(world: World): List<EntityId> {
    val stoneVisual = EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.WARD_STONE.ordinal.toLong())
    val stones = mutableListOf<EntityId>()
    world.each(EntityVisual::class) { id, visual ->
      if (visual == stoneVisual) {
        stones.add(id)
      }
    }

    return stones
  }

  private fun carriesAura(world: World, stone: EntityId): Boolean {
    return world.get(stone, StatusEffects::class)?.hasEffect(StatusEffectId.WARD_AURA.id) == true
  }
}

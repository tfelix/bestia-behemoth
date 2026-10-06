package net.bestia.zone.battle

import io.mockk.mockk
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.ecs.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.prop.PropPromotionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class BattleContextFactoryTest {

  // Every entity here already carries Position, so PropPromotionService.promoteIfNeeded short-circuits
  // immediately and never touches the divergence registry - a relaxed mock is enough.
  private val factory = BattleContextFactory(PropPromotionService(mockk(relaxed = true)))

  /**
   * Regression for the mob-seeding fix: an entity with [StatusValues] but no Level component must be
   * projectable into a [BattleEntity] as both attacker and defender, read as level 1. Before the fix
   * `battleEntity` returned null on the missing StatusValues, so no mob could ever fight.
   */
  @Test
  fun `a mob-shaped entity (StatusValues, no Level) yields a non-null battle context`() {
    val world = testWorld()

    val attacker = world.spawnMobLike()
    val defender = world.spawnMobLike()

    val ctx = world.read {
      factory.create(
        world = world,
        attackerId = attacker,
        usedAttack = BattleContextFixture.attack(),
        targetEntityId = defender,
        targetPosition = null
      )
    }

    assertNotNull(ctx, "a mob with StatusValues must produce a battle context")
  }

  @Test
  fun `a defender is hit as its species' element, and as NORMAL without one`() {
    val world = testWorld()

    val attacker = world.spawnMobLike()
    val fiery = world.spawnMobLike(Nature(Element.FIRE_2, Size.SMALL))
    val plain = world.spawnMobLike()

    fun defenderElement(defender: EntityId) = world.read {
      val ctx = factory.create(
        world = world,
        attackerId = attacker,
        usedAttack = BattleContextFixture.attack(),
        targetEntityId = defender,
        targetPosition = null
      ) as EntityBattleContext
      ctx.defender.assumedElement
    }

    assertEquals(Element.FIRE_2, defenderElement(fiery))
    assertEquals(Element.NORMAL, defenderElement(plain))
  }

  private fun World.spawnMobLike(nature: Nature? = null): EntityId = createEntity { id ->
    nature?.let { add(id, it) }
    add(id, Position.fromVec3(Vec3L(1L, 0L, 0L)))
    add(id, StatusValues(strength = 10, intelligence = 10, vitality = 10, dexterity = 10, willpower = 10, agility = 10))
  }
}

package net.bestia.zone.battle

import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class BattleContextFactoryTest {

  private val factory = BattleContextFactory()

  /**
   * Regression for the mob-seeding fix: a mob now carries [StatusValues] (but no Level component),
   * so it must be projectable into a [BattleEntity] as both attacker and defender. Before the fix
   * `battleEntity` returned null on the missing StatusValues, so no mob could ever fight.
   */
  @Test
  fun `a mob-shaped entity (StatusValues, no Level) yields a non-null battle context`() {
    val world = testWorld()

    val attacker = world.spawnMobLike()
    val defender = world.spawnMobLike()

    val ctx = world.locked {
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

  private fun World.spawnMobLike(): EntityId = createEntity { id ->
    add(id, Position.fromVec3(Vec3L(1L, 0L, 0L)))
    add(id, StatusValues(strength = 10, intelligence = 10, vitality = 10, dexterity = 10, willpower = 10, agility = 10))
  }
}

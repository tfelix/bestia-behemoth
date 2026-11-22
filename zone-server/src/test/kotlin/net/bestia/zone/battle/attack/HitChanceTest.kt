package net.bestia.zone.battle.attack

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Miss
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `HIT - FLEE` percent: about three swings in four land between equals, at level 1 as at level 100. */
class HitChanceTest {

  private fun lands(roll: Float, level: Int, attributes: Int = 10): Boolean {
    val factory = AttackStrategyFactory(LineOfSightService(), FixedRandom(roll))
    val ctx = BattleContextFixture.entityCtx(
      attackerEntity = BattleContextFixture.battleEntity(level = level, dexterity = attributes, agility = attributes),
      defenderEntity = BattleContextFixture.battleEntity(
        level = level, dexterity = attributes, agility = attributes, id = BattleContextFixture.DEFENDER_ID
      ),
    )

    return factory.getAttackStrategy(ctx).execute(ctx) !is Miss
  }

  @Test
  fun `equals land three in four at any level`() {
    for (level in listOf(1, 50, 100)) {
      assertTrue(lands(0.70f, level, attributes = level), "level $level should land a 0.70 roll")
      assertFalse(lands(0.80f, level, attributes = level), "level $level should miss a 0.80 roll")
    }
  }
}

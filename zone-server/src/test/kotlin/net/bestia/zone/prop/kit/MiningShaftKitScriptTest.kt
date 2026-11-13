package net.bestia.zone.prop.kit

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.mining.MiningSkill
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.skillIdsOf
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiningShaftKitScriptTest {

  private val standingAt = Vec3L(100, 100, 64)
  private val world = testWorld()

  private val structures = mockk<PlayerStructureService> {
    every { beginConstruction(any(), any(), any(), any(), any(), any(), any()) } returns 1L
  }

  private val script = MiningShaftKitScript(structures, MiningSkill(skillIdsOf(SkillId.M_MINING to MINING_ID)))

  @Test
  fun `a miner puts up a shaft`() {
    val miner = spawnMaster(miningLevel = 1)

    assertTrue(script.execute(world, miner, ScriptArgs.of(ScriptArgKeys.POSITION to standingAt)))
  }

  @Test
  fun `a master who never learned Mining keeps the kit`() {
    val novice = spawnMaster(miningLevel = 0)

    assertFalse(script.execute(world, novice, ScriptArgs.of(ScriptArgKeys.POSITION to standingAt)))
  }

  private fun spawnMaster(miningLevel: Int): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(standingAt))
      add(id, Master(7L))
      add(id, Account(3L))
      add(id, KnownSkills(mutableMapOf(MINING_ID to miningLevel)))
    }
  }

  private companion object {
    const val MINING_ID = 40L
  }
}

package net.bestia.zone.casting.scripts.master

import io.mockk.mockk
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.casting.PotionThrowing
import net.bestia.zone.casting.ReagentPayment
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.RecordingSkillWorld.ThrownPotion
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.script.BreadScript
import net.bestia.zone.item.script.SmallHealthPotionScript
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlchemistSkillsTest {

  private val targetId = BattleContextFixture.DEFENDER_ID

  @Test
  fun `a thrown potion is 10 percent stronger per level`() {
    assertEquals(listOf(110, 130, 150, 150), listOf(1, 3, 5, 9).map(PotionPitcher::strengthPercent))
  }

  @Test
  fun `Potion Pitcher throws the picked potion at its target`() {
    val world = RecordingSkillWorld()

    PotionPitcher(LineOfSightService()).execute(ctx(world, skillLevel = 4, args = listOf(SMALL_HEALTH_POTION)))

    assertEquals(listOf(ThrownPotion(targetId, SMALL_HEALTH_POTION, 140)), world.thrownPotions)
  }

  @Test
  fun `a throw the world refuses at resolution is no cast at all`() {
    val world = RecordingSkillWorld().apply { potionRefusal = OpError.SKILL_INVALID_CHOICE }
    val pitcher = PotionPitcher(LineOfSightService())

    assertFalse(pitcher.isCastPossible(ctx(world, skillLevel = 1, args = listOf(SMALL_HEALTH_POTION))))
    assertFalse(pitcher.isCastPossible(ctx(RecordingSkillWorld(), skillLevel = 1, args = emptyList())))
  }

  @Test
  fun `the button refuses no choice, a held loaf, a potion not held and a target on the other side`() {
    val world = testWorld()
    val caster = world.createEntity { id ->
      add(id, Master(masterId = 1L))
      add(id, Inventory(mutableListOf(Inventory.Item(SMALL_HEALTH_POTION, 2), Inventory.Item(BREAD, 1))))
    }
    val ally = world.createEntity { id -> add(id, Master(masterId = 2L)) }
    val wild = world.createEntity { id -> add(id, Health(10, 10)) }
    val pitcher = PotionPitcher(LineOfSightService(), throwing())

    fun refusal(target: EntityId, vararg args: Long): OpError? {
      return pitcher.checkTarget(world, caster, target, skillLevel = 1, args = args.toList())
    }

    assertEquals(OpError.SKILL_INVALID_CHOICE, refusal(ally))
    assertEquals(OpError.SKILL_INVALID_CHOICE, refusal(ally, BREAD))
    assertEquals(OpError.SKILL_INVALID_CHOICE, refusal(ally, SMALL_MANA_POTION))
    assertEquals(OpError.SKILL_NEEDS_ALLY, refusal(wild, SMALL_HEALTH_POTION))
    assertNull(refusal(ally, SMALL_HEALTH_POTION))
    assertNull(refusal(caster, SMALL_HEALTH_POTION), "a Pitcher may throw at their own feet")
  }

  @Test
  fun `a thrown potion heals the ally and leaves the bag one lighter`() {
    val world = testWorld()
    val caster = world.createEntity { id ->
      add(id, Master(masterId = 1L))
      add(id, Inventory(mutableListOf(Inventory.Item(SMALL_HEALTH_POTION, 2))))
    }
    val ally = world.createEntity { id ->
      add(id, Master(masterId = 2L))
      add(id, Health(1, 500))
    }

    assertTrue(throwing().throwAt(world, caster, ally, SMALL_HEALTH_POTION, strengthPercent = 150))

    // 45 at 150 percent
    assertEquals(1 + 67, world.get(ally, Health::class)!!.current)
    assertEquals(1, world.get(caster, Inventory::class)!!.getItems().single().amount)
  }

  @Test
  fun `a potion that restores nothing stays in the bag`() {
    val world = testWorld()
    val caster = world.createEntity { id ->
      add(id, Master(masterId = 1L))
      add(id, Inventory(mutableListOf(Inventory.Item(SMALL_HEALTH_POTION, 1))))
    }
    val noHealth = world.createEntity { id -> add(id, Master(masterId = 2L)) }

    assertFalse(throwing().throwAt(world, caster, noHealth, SMALL_HEALTH_POTION, strengthPercent = 150))

    assertEquals(1, world.get(caster, Inventory::class)!!.getItems().single().amount)
  }

  private fun throwing(): PotionThrowing {
    return PotionThrowing(
      listOf(SmallHealthPotionScript(mockk(relaxed = true)), BreadScript()),
      ReagentPayment(mockk(relaxed = true), mockk(relaxed = true)),
    )
  }

  private fun ctx(world: RecordingSkillWorld, skillLevel: Int, args: List<Long>) =
    SkillContextFixture.skillCtx(BattleContextFixture.entityCtx(), world, skillLevel = skillLevel, args = args)

  private companion object {
    const val SMALL_HEALTH_POTION = 3L
    const val SMALL_MANA_POTION = 33L
    const val BREAD = 30L
  }
}

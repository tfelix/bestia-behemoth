package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ai.profile.Courage
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * An owned bestia joining the fights of its master and its master's other bestias. The attacker is a grazer, which
 * never goes for the helper by itself, so only helping can bring the two together.
 */
class HelpInFightsTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(0, 0, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  private fun helper(courage: Courage = Courage.STEADY, staysOutOfFights: Boolean = false): EntityId {
    val orders = AiConfig(BestiaJob.REST, courage, staysOutOfFights = staysOutOfFights)
    val id = ai.spawnMob("passive_wanderer", home, orders = orders)
    ai.world.add(id, Level(HELPER_LEVEL))
    ai.world.add(id, OwnedBestia(masterId = MASTER_ID, playerBestiaId = id))
    ai.world.add(id, Account(MASTER_ID * 10))
    return id
  }

  private fun master(masterId: Long, pos: Vec3L): EntityId {
    val id = ai.spawnPlayer(pos)
    ai.world.getOrThrow(id, Master::class).masterId = masterId
    ai.world.add(id, Account(masterId * 10))
    return id
  }

  private fun grazer(pos: Vec3L, level: Int = HELPER_LEVEL): EntityId {
    val id = ai.spawnMob("passive_wanderer", pos)
    ai.aoi.setEntityPosition(id, pos)
    ai.world.add(id, Level(level))
    return id
  }

  private fun targetOf(id: EntityId): EntityId? {
    return ai.beliefOf(id, BestiaDomain.TARGET_ID)
  }

  private fun assertNeverHelps(helper: EntityId, why: String) {
    repeat(20 * 5) {
      ai.tick()
      assertNotEquals("HelpAlly", ai.goalNameOf(helper), why)
    }
  }

  @Test
  fun `a resting bestia joins the fight when its master is attacked`() {
    val helper = helper()
    val master = master(MASTER_ID, Vec3L(3, 0, 0))
    val foe = grazer(Vec3L(5, 0, 0))
    ai.recordHit(master, foe)

    ai.tickUntilGoal(helper, "HelpAlly")

    assertEquals(foe, targetOf(helper))
  }

  @Test
  fun `a bestia joins the fight when another of its master's bestias is attacked`() {
    val helper = helper()
    val sibling = ai.spawnOwnedBestia(Vec3L(3, 0, 0), masterId = MASTER_ID)
    val foe = grazer(Vec3L(5, 0, 0))
    ai.recordHit(sibling, foe)

    ai.tickUntilGoal(helper, "HelpAlly")

    assertEquals(foe, targetOf(helper))
  }

  @Test
  fun `a bestia told to stay out of fights leaves its master's fight alone`() {
    val helper = helper(staysOutOfFights = true)
    val master = master(MASTER_ID, Vec3L(3, 0, 0))
    ai.recordHit(master, grazer(Vec3L(5, 0, 0)))

    assertNeverHelps(helper, "it joined a fight its orders keep it out of")
  }

  @Test
  fun `a careful bestia leaves a stronger foe to its master`() {
    val helper = helper(Courage.CAREFUL)
    val master = master(MASTER_ID, Vec3L(3, 0, 0))
    ai.recordHit(master, grazer(Vec3L(5, 0, 0), level = HELPER_LEVEL + BestiaDomain.STRONGER_FOE_LEVEL_LEAD))

    assertNeverHelps(helper, "a careful bestia took on a stronger foe")
  }

  @Test
  fun `a timid bestia never joins a fight`() {
    val helper = helper(Courage.TIMID)
    val master = master(MASTER_ID, Vec3L(3, 0, 0))
    ai.recordHit(master, grazer(Vec3L(5, 0, 0)))

    assertNeverHelps(helper, "a timid bestia joined a fight")
  }

  @Test
  fun `a bestia leaves another master's fight alone`() {
    val helper = helper()
    val stranger = master(STRANGER_ID, Vec3L(3, 0, 0))
    val foe = grazer(Vec3L(5, 0, 0))
    ai.recordHit(stranger, foe)

    assertNeverHelps(helper, "it joined a stranger's fight")
    assertNotEquals(foe, targetOf(helper))
  }

  private companion object {
    const val HELPER_LEVEL = 10
    const val MASTER_ID = 7L
    const val STRANGER_ID = 2L
  }
}

package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A bestia under GUARD orders holding the area around its home. The wolf profile's area is 24 around home. */
class GuardTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(0, 0, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  private fun guard(): EntityId {
    val id = ai.spawnMob("aggressive_melee", home, orders = AiConfig(BestiaJob.GUARD))
    ai.world.add(id, OwnedBestia(masterId = MASTER_ID, playerBestiaId = id))
    ai.world.add(id, Account(MASTER_ACCOUNT))
    return id
  }

  /** A wild creature the guard can see, as the AOI index finds a spawned mob. */
  private fun wild(profileId: String, pos: Vec3L): EntityId {
    val id = ai.spawnMob(profileId, pos)
    ai.aoi.setEntityPosition(id, pos)
    return id
  }

  private fun targetOf(id: EntityId): EntityId? {
    return ai.beliefOf(id, BestiaDomain.TARGET_ID)
  }

  @Test
  fun `a guard attacks a stranger inside its area`() {
    val guard = guard()
    val stranger = ai.spawnPlayer(Vec3L(5, 0, 0))

    ai.tickUntilGoal(guard, "KillEnemy")

    assertEquals(stranger, targetOf(guard))
  }

  @Test
  fun `a guard lets a stranger go once it leaves the area and walks back to the middle`() {
    val guard = guard()
    ai.teleport(guard, Vec3L(20, 0, 0))
    val stranger = ai.spawnPlayer(Vec3L(23, 0, 0))
    ai.tickUntilGoal(guard, "KillEnemy")

    ai.teleport(stranger, Vec3L(27, 0, 0))

    ai.tickUntilGoal(guard, "ReturnToMiddle")
    ai.tickUntil(describe = { "it stopped at ${ai.positionOf(guard)}" }) {
      ai.positionOf(guard).distance(home) <= BestiaDomain.ARRIVAL_RADIUS
    }
  }

  @Test
  fun `a guard does not chase an attacker out of its area`() {
    val guard = guard()
    val stranger = ai.spawnPlayer(Vec3L(27, 0, 0))
    ai.teleport(guard, Vec3L(20, 0, 0))

    repeat(20 * 5) {
      ai.recordHit(guard, stranger)
      ai.tick()
      assertNotEquals(stranger, targetOf(guard), "it went after an attacker outside its area")
    }
  }

  @Test
  fun `a guard attacks a wild hunter inside its area`() {
    val guard = guard()
    val wolf = wild("aggressive_melee", Vec3L(5, 0, 0))

    ai.tickUntilGoal(guard, "KillEnemy")

    assertEquals(wolf, targetOf(guard))
  }

  @Test
  fun `a guard leaves a grazer alone`() {
    val guard = guard()
    val deer = wild("passive_wanderer", Vec3L(5, 0, 0))

    repeat(20 * 5) {
      ai.tick()
      assertNotEquals(deer, targetOf(guard), "it attacked a grazer that never attacked it")
    }
  }

  @Test
  fun `a guard ignores a wild hunter outside its area`() {
    val guard = guard()
    ai.teleport(guard, Vec3L(20, 0, 0))
    val wolf = wild("aggressive_melee", Vec3L(27, 0, 0))

    repeat(20 * 5) {
      ai.tick()
      assertNotEquals(wolf, targetOf(guard), "it went after a wolf outside its area")
    }
  }

  private companion object {
    const val MASTER_ID = 7L
    const val MASTER_ACCOUNT = 70L
  }
}

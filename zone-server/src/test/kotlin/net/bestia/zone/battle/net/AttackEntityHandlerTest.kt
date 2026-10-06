package net.bestia.zone.battle.net

import io.mockk.mockk
import net.bestia.zone.battle.attack.AttackExecutionService
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.ecs.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.logout.ecs.LogoutCancelService
import net.bestia.zone.logout.ecs.LogoutIntent
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.prop.PropPromotionService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.LineOfSightService

/**
 * A player's click reaches the same [AttackExecutionService] a mob's bite does, rather than the random number
 * this handler used to invent for itself - which is what left the damage formula unreachable from the one
 * pathway players could actually see.
 */
class AttackEntityHandlerTest {

  private val world = testWorld()

  /** Always draws zero, so every swing lands - the point here is the pathway, not the roll. */
  private val alwaysLands = FixedRandom(0f)

  private fun handlerFor(attacker: EntityId): AttackEntityHandler {
    val connectionInfoService = ConnectionInfoService()
    connectionInfoService.activateSession(ACCOUNT_ID, masterId = 1L, masterEntityId = attacker)

    return AttackEntityHandler(
      connectionInfoService = connectionInfoService,
      attackExecutionService = AttackExecutionService(
        BattleContextFactory(PropPromotionService(mockk(relaxed = true))),
        AttackStrategyFactory(LineOfSightService(), alwaysLands),
        mockk(relaxed = true)
      ),
      logoutCancelService = LogoutCancelService(),
      deadActionGuard = DeadActionGuard(),
      propPromotion = PropPromotionService(mockk(relaxed = true)),
    )
  }

  @Test
  fun `a click on a target in reach stages real damage on it`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))

    handlerFor(attacker).handle(world, AttackEntityCMSG(playerId = ACCOUNT_ID, targetEntityId = target))

    assertTrue(world.has(target, IncomingDamage::class), "a swing in reach must resolve onto the target")
  }

  @Test
  fun `a target out of the swing's reach is not hit`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(50, 0, 0))

    handlerFor(attacker).handle(world, AttackEntityCMSG(playerId = ACCOUNT_ID, targetEntityId = target))

    assertFalse(
      world.has(target, IncomingDamage::class),
      "range is enforced by the attack pathway; the old handler checked nothing at all"
    )
  }

  @Test
  fun `swinging at something aborts a pending logout`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))
    world.add(attacker, LogoutIntent())

    handlerFor(attacker).handle(world, AttackEntityCMSG(playerId = ACCOUNT_ID, targetEntityId = target))

    assertFalse(world.has(attacker, LogoutIntent::class))
  }

  private fun World.spawnFighter(at: Vec3L): EntityId = createEntity { id ->
    add(id, Position.fromVec3(at))
    add(id, StatusValues(strength = 10, intelligence = 10, vitality = 10, dexterity = 10, willpower = 10, agility = 10))
  }

  private companion object {
    const val ACCOUNT_ID = 1L
  }
}

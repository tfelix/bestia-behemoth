package net.bestia.zone.battle.status.scripts

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test

class AuraScriptTest {

  private class TestSong(aoi: EntityAOIService) : AuraScript(aoi) {
    override val granted = StatusEffectId.SWIFTNESS
    override fun durationSeconds(level: Int) = 60.0
    override fun radiusTiles(world: World, bearerId: EntityId, effect: ActiveStatusEffect) = 3L
    override fun reaches(world: World, bearerId: EntityId, otherId: EntityId) = isFriendOf(world, bearerId, otherId)
  }

  private val world = testWorld()
  private val aoi = mockk<EntityAOIService>()
  private val statusEffects = mockk<StatusEffectService>(relaxed = true)

  @Test
  fun `a pulse grants the song to friends in the field for the linger time, not to foes or the far away`() {
    val bard = player(Vec3L(0, 0, 0))
    val friend = player(Vec3L(2, 0, 0))
    val farFriend = player(Vec3L(9, 0, 0))
    val wolf = world.createEntity { id -> add(id, Position.fromVec3(Vec3L(1, 0, 0))) }
    every { aoi.queryEntitiesInCube(any(), any(), any()) } returns setOf(bard, friend, farFriend, wolf)
    val effect = ActiveStatusEffect(definitionId = 999L, level = 2, remainingSeconds = 60f)

    TestSong(aoi).onTick(StatusEffectTickContext(world, bard, effect, statusEffects))

    verify { statusEffects.applyEffect(world, friend, StatusEffectId.SWIFTNESS, 2, bard, 5.0, 0) }
    verify { statusEffects.applyEffect(world, bard, StatusEffectId.SWIFTNESS, 2, bard, 5.0, 0) }
    verify(exactly = 0) { statusEffects.applyEffect(world, wolf, any<StatusEffectId>(), any(), any(), any(), any()) }
    verify(exactly = 0) { statusEffects.applyEffect(world, farFriend, any<StatusEffectId>(), any(), any(), any(), any()) }
  }

  private fun player(at: Vec3L): EntityId = world.createEntity { id ->
    add(id, Master(id))
    add(id, Position.fromVec3(at))
  }
}

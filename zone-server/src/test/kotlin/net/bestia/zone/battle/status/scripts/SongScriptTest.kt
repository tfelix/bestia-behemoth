package net.bestia.zone.battle.status.scripts

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SongScriptTest {

  private class TestSong(aoi: EntityAOIService, songcraft: Songcraft) : SongScript(aoi, songcraft) {
    override val granted = StatusEffectId.SWIFTNESS
    override val baseSeconds = 60.0
  }

  private val world = testWorld()
  private val aoi = mockk<EntityAOIService>()
  private val statusEffects = mockk<StatusEffectService>(relaxed = true)
  private val skills = mockk<SkillRepository> {
    every { findByIdentifier("M_VOICE_TRAINING") } returns mockk<Skill> { every { id } returns VOICE_TRAINING_ID }
    every { findByIdentifier("M_GRAND_PERFORMANCE") } returns mockk<Skill> { every { id } returns GRAND_PERFORMANCE_ID }
  }
  private val song = TestSong(aoi, Songcraft(skills))
  private val field = ActiveStatusEffect(definitionId = 999L, level = 4, remainingSeconds = 60f)

  @Test
  fun `an untrained Bard's song reaches 3 tiles for 60 s at its table strength`() {
    val bard = bard()
    val friend = player(Vec3L(3, 0, 0))
    val beyond = player(Vec3L(4, 0, 0))
    every { aoi.queryEntitiesInCube(any(), any(), any()) } returns setOf(bard, friend, beyond)

    song.onTick(StatusEffectTickContext(world, bard, field, statusEffects))

    verify { statusEffects.applyEffect(world, friend, StatusEffectId.SWIFTNESS, 4, bard, 5.0, 100) }
    verify(exactly = 0) { statusEffects.applyEffect(world, beyond, any<StatusEffectId>(), any(), any(), any(), any()) }
    assertEquals(60.0, song.durationSeconds(world, bard, 4))
  }

  @Test
  fun `Voice Training widens the field a tile and lengthens the song 10 percent a level, Grand Performance strengthens it 10 percent`() {
    val bard = bard(voiceTraining = 2, grandPerformance = 3)
    val friend = player(Vec3L(0, 5, 0))
    val beyond = player(Vec3L(0, 6, 0))
    every { aoi.queryEntitiesInCube(any(), any(), any()) } returns setOf(bard, friend, beyond)

    song.onTick(StatusEffectTickContext(world, bard, field, statusEffects))

    verify { statusEffects.applyEffect(world, friend, StatusEffectId.SWIFTNESS, 4, bard, 5.0, 130) }
    verify(exactly = 0) { statusEffects.applyEffect(world, beyond, any<StatusEffectId>(), any(), any(), any(), any()) }
    assertEquals(72.0, song.durationSeconds(world, bard, 4))
    assertEquals(19, Songcraft.boosted(15, 130), "rounded down")
  }

  @Test
  fun `the song ends when the Bard puts the instrument away, falls or cannot cast`() {
    val withoutInstrument = bard(held = WeaponType.WHIP)
    val fallen = bard().also { world.add(it, Dead()) }
    val silenced = bard().also {
      world.add(it, StatusEffects(mutableListOf(ActiveStatusEffect(1L, 1, 10f, locks = setOf(ActionLock.CAST)))))
    }
    every { aoi.queryEntitiesInCube(any(), any(), any()) } returns emptySet()

    for (bard in listOf(withoutInstrument, fallen, silenced)) {
      song.onTick(StatusEffectTickContext(world, bard, field, statusEffects))

      verify { statusEffects.removeEffect(world, bard, field.definitionId, RemovalReason.CANCELLED) }
    }
    verify(exactly = 0) { statusEffects.applyEffect(world, any(), any<StatusEffectId>(), any(), any(), any(), any()) }
  }

  private fun bard(voiceTraining: Int = 0, grandPerformance: Int = 0, held: WeaponType = WeaponType.INSTRUMENT) =
    player(Vec3L(0, 0, 0)).also { id ->
      world.add(id, KnownSkills(mutableMapOf(VOICE_TRAINING_ID to voiceTraining, GRAND_PERFORMANCE_ID to grandPerformance)))
      world.add(id, CombatStats(CombatValues.ofBase(1, StatusValues(1, 1, 1, 1, 1, 1)).copy(heldWeaponType = held)))
    }

  private fun player(at: Vec3L): EntityId = world.createEntity { id ->
    add(id, Master(id))
    add(id, Position.fromVec3(at))
  }

  private companion object {
    const val VOICE_TRAINING_ID = 165L
    const val GRAND_PERFORMANCE_ID = 170L
  }
}

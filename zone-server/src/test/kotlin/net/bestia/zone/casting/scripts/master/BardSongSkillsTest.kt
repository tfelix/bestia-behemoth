package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BardSongSkillsTest {

  private val caster = BattleContextFixture.ATTACKER_ID
  private val world = RecordingSkillWorld(entitiesNearby = setOf(caster))
  private val ecs = testWorld()

  @Test
  fun `each song starts its field on the Bard at the skill's level`() {
    world.heldWeapons[caster] = WeaponType.INSTRUMENT

    SONGS.forEach { (song, _) -> song.execute(ctx(level = 3)) }

    assertEquals(SONGS.map { (_, field) -> Triple(caster, field.id, 3) }, world.appliedEffects.map {
      Triple(it.targetEntityId, it.effectId, it.level)
    })
  }

  @Test
  fun `a song needs an instrument`() {
    SONGS.forEach { (song, _) ->
      assertEquals(OpError.SKILL_NEEDS_INSTRUMENT, song.checkCastStart(ecs, bardWith(WeaponType.WHIP), 1, emptyList()))
      assertNull(song.checkCastStart(ecs, bardWith(WeaponType.INSTRUMENT), 1, emptyList()))
      assertFalse(song.isCastPossible(ctx(level = 1)), "nothing held at resolution")
    }
  }

  private fun bardWith(weapon: WeaponType?): EntityId {
    return ecs.createEntity { id ->
      add(id, CombatStats(CombatValues.ofBase(1, StatusValues(1, 1, 1, 1, 1, 1)).copy(heldWeaponType = weapon)))
    }
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(world = world, skillLevel = level)

  private companion object {
    val SONGS = listOf(
      BattleHymn() to StatusEffectId.SONG_BATTLE_HYMN,
      WarMarch() to StatusEffectId.SONG_WAR_MARCH,
      MirageDance() to StatusEffectId.SONG_MIRAGE_DANCE,
      WanderersTune() to StatusEffectId.SONG_WANDERERS_TUNE,
    )
  }
}

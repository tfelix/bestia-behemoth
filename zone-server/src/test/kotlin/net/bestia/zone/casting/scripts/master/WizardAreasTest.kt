package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WizardAreasTest {

  private val world = RecordingSkillWorld()

  @Test
  fun `Fire Wall lays three burning tiles across the cast that throw foes back a tile per level`() {
    // The caster stands at x = 1 and aims at x = 3, so the wall runs along y.
    FireWall(LineOfSightService()).execute(groundCtx(level = 2, element = Element.FIRE))

    assertEquals(listOf(Vec3L(3, -1, 0), Vec3L(3, 0, 0), Vec3L(3, 1, 0)), world.spawnedAreaEffects.map { it.centre })
    val tile = world.spawnedAreaEffects.first()
    assertEquals(7L, tile.visualId)
    val flames = tile.effect
    assertEquals(0L, flames.radiusTiles)
    // 15 MATK at 130%.
    assertEquals(19, flames.damagePerTick)
    assertEquals(40, flames.remainingTicks)
    assertEquals(Element.FIRE, flames.element)
    assertEquals(2, flames.mechanics.knockbackTiles)
    assertTrue(flames.mechanics.knockbackAgainstFacing)
    assertTrue(flames.mechanics.landsAsElementalSpell)
  }

  @Test
  fun `Meteor Storm drops two more meteors per level over ten seconds, each splashing a seven by seven`() {
    MeteorStorm(LineOfSightService()).execute(groundCtx(level = 3, element = Element.FIRE, aoeRadius = 3.0))

    val spawned = world.spawnedAreaEffects.single()
    assertEquals(8L, spawned.visualId)
    val storm = spawned.effect
    assertEquals(7, storm.remainingTicks)
    assertEquals(10f / 7, storm.tickIntervalSeconds)
    // 15 MATK at 150%.
    assertEquals(22, storm.damagePerTick)
    assertEquals(3L, storm.radiusTiles)
    assertEquals(3, storm.mechanics.strikeRadiusTiles)
    assertTrue(storm.mechanics.landsAsElementalSpell)
  }

  @Test
  fun `Thunder Storm calls three strikes at Lv1 over eight seconds, each on a three by three`() {
    ThunderStorm(LineOfSightService()).execute(groundCtx(level = 1, element = Element.WIND, aoeRadius = 2.0))

    val spawned = world.spawnedAreaEffects.single()
    assertEquals(10L, spawned.visualId)
    val storm = spawned.effect
    assertEquals(3, storm.remainingTicks)
    assertEquals(8f / 3, storm.tickIntervalSeconds)
    // 15 MATK at 110%.
    assertEquals(16, storm.damagePerTick)
    assertEquals(Element.WIND, storm.element)
    assertEquals(1, storm.mechanics.strikeRadiusTiles)
  }

  private fun groundCtx(level: Int, element: Element, aoeRadius: Double? = null) = SkillContextFixture.skillCtx(
    BattleContextFixture.groundCtx(BattleContextFixture.attack(level = level, aoeRadius = aoeRadius, element = element)),
    world,
    skillLevel = level,
  )
}

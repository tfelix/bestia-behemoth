package net.bestia.zone.ai.perception

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoticeRangeTest {

  private val world = testWorld()
  private val wild = world.createEntity { }
  private val tamed = world.createEntity { world.add(it, OwnedBestia(masterId = 2L, playerBestiaId = it)) }
  private val hunter = world.createEntity {
    world.add(it, Master(1L))
    world.add(it, CombatStats(CombatValues.ofBase(1, StatusValues(1, 1, 1, 1, 1, 1)).copy(noticeRangeCutPercent = 30)))
  }
  private val plainMaster = world.createEntity { world.add(it, Master(3L)) }

  private val here = Vec3L(0, 0, 0)

  @Test
  fun `the wild notices a camouflaged master only within the cut sight radius`() {
    assertTrue(NoticeRange.notices(world, wild, hunter, here, Vec3L(7, 3, 0), SIGHT))
    assertFalse(NoticeRange.notices(world, wild, hunter, here, Vec3L(8, 0, 0), SIGHT))
  }

  @Test
  fun `a master without Camouflage is noticed anywhere in the sight cube`() {
    assertTrue(NoticeRange.notices(world, wild, plainMaster, here, Vec3L(10, 10, 0), SIGHT))
  }

  @Test
  fun `a tamed bestia sees through the camouflage of a master`() {
    assertTrue(NoticeRange.notices(world, tamed, hunter, here, Vec3L(10, 0, 0), SIGHT))
  }

  private companion object {
    const val SIGHT = 10
  }
}

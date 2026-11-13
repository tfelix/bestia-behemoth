package net.bestia.zone.item.script

import io.mockk.mockk
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestorativeScriptTest {

  private val world = testWorld()

  @Test
  fun `a use restores the item's amounts on its user`() {
    val drinker = withVitals()

    assertTrue(MedicineScript(mockk(relaxed = true)).execute(world, drinker, ScriptArgs.EMPTY))

    assertEquals(70, world.get(drinker, Health::class)!!.current)
    assertEquals(30, world.get(drinker, Stamina::class)!!.current)
  }

  @Test
  fun `a restore at 150 percent gives half as much again to whoever it reaches`() {
    val drinker = withVitals()

    assertTrue(MedicineScript(mockk(relaxed = true)).restore(world, drinker, strengthPercent = 150))
    assertTrue(SmallManaPotionScript().restore(world, drinker, strengthPercent = 150))

    assertEquals(105, world.get(drinker, Health::class)!!.current)
    assertEquals(45, world.get(drinker, Stamina::class)!!.current)
    assertEquals(67, world.get(drinker, Mana::class)!!.current)
  }

  @Test
  fun `a potion restores nothing on a body without that vital`() {
    val noPool = world.createEntity { id -> add(id, Health(1, 500)) }

    assertFalse(SmallManaPotionScript().restore(world, noPool, strengthPercent = 150))
  }

  @Test
  fun `only the drinkable restoratives are potions`() {
    val messages = mockk<OutMessageProcessor>(relaxed = true)

    val potions = listOf(SmallHealthPotionScript(messages), SmallManaPotionScript(), MedicineScript(messages))
    val others = listOf(
      BandageScript(messages), NoviceHealthPotionScript(messages), NoviceManaPotionScript(), BreadScript(), AleScript(),
      HeartyStewScript(messages),
    )

    assertTrue(potions.all { it.isPotion })
    assertTrue(others.none { it.isPotion })
  }

  private fun withVitals(): EntityId {
    return world.createEntity { id ->
      add(id, Health(0, 500))
      add(id, Stamina(0, 500))
      add(id, Mana(0, 500))
    }
  }
}

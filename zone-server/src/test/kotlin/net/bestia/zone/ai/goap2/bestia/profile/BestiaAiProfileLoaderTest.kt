package net.bestia.zone.ai.goap2.bestia.profile

import net.bestia.zone.ai.goap2.bestia.BestiaDomain
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BestiaAiProfileLoaderTest {

  @Test
  fun `loads the wolf archetype from resources and attaches it to a fresh agent`() {
    val profiles = BestiaAiProfileLoader().loadAll()
    val wolf = profiles.single { it.identifier == "wolf" }

    assertEquals("wolf_pack", wolf.faction)
    assertEquals(6L, wolf.wanderRadius)
    assertEquals(4, wolf.goals.size)
    assertEquals(listOf("bite"), wolf.attacks.map { it.id })

    val home = Vec3L(10, 10, 0)
    val agent = BestiaAgentFactory.create(wolf, homePosition = home)

    assertEquals(4, agent.goals.size)
    assertEquals(95f, agent.goals.single { it.name == "KillAttacker" }.priority.base)
    assertEquals(home, agent.memory.get(BestiaDomain.HOME_POSITION))
    assertEquals(6L, agent.memory.get(BestiaDomain.WANDER_RADIUS))
    assertTrue(BestiaDomain.actionTemplates().keys.containsAll(setOf("wander", "attack")))
  }
}

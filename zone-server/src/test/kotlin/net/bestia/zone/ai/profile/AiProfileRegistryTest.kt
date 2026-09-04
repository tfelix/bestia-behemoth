package net.bestia.zone.ai.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Boot-time behaviour of the one AI profile registry: the shipped archetypes parse, and a profile naming
 * something the domain does not define fails the boot rather than producing a mob that silently does nothing.
 */
class AiProfileRegistryTest {

  private fun loadedRegistry() = AiProfileRegistry().apply { load() }

  @Test
  fun `loads every shipped archetype from the classpath`() {
    val registry = loadedRegistry()

    assertNotNull(registry.get("aggressive_melee"))
    assertNotNull(registry.get("passive_wanderer"))
  }

  @Test
  fun `parses tuning knobs and attacks`() {
    val profile = loadedRegistry().getOrThrow("aggressive_melee")

    assertEquals(8, profile.perception.sightRadius)
    assertEquals(80, profile.tuning.aggression)
    assertEquals(listOf("claw"), profile.attacks.map { it.id })
    assertEquals(1L, profile.attacks.single().range)
  }

  @Test
  fun `a peaceful archetype still retaliates`() {
    val profile = loadedRegistry().getOrThrow("passive_wanderer")

    // Being attacked is not something a profile opts into for flavour, so even the harmless grazer carries
    // the retaliation goal — gated on having actually been hit.
    assertTrue(profile.goals.any { it.name == "KillAttacker" })
    assertTrue(profile.goals.none { it.name == "KillEnemy" }, "a critter must not pick fights")
  }

  @Test
  fun `an unknown action id fails fast`() {
    val registry = AiProfileRegistry()
    val dto = AiProfileDto(
      identifier = "broken",
      goals = listOf(AiProfileDto.GoalDto("Sleep")),
      actions = listOf("sleep", "doesNotExist"),
    )

    val error = assertThrows<IllegalArgumentException> { registry.register(dto) }
    assertTrue(error.message!!.contains("doesNotExist"))
  }

  @Test
  fun `an unknown goal name fails fast`() {
    val registry = AiProfileRegistry()
    val dto = AiProfileDto(
      identifier = "broken",
      goals = listOf(AiProfileDto.GoalDto("BecomeEmperor")),
      actions = listOf("sleep"),
    )

    val error = assertThrows<IllegalArgumentException> { registry.register(dto) }
    assertTrue(error.message!!.contains("BecomeEmperor"))
  }

  @Test
  fun `goals with no actions to satisfy them fail fast`() {
    val registry = AiProfileRegistry()
    val dto = AiProfileDto(
      identifier = "inert",
      goals = listOf(AiProfileDto.GoalDto("Sleep")),
      actions = emptyList(),
    )

    // Otherwise this spawns a mob that selects a goal, fails to plan, and retries forever — cheap to catch
    // here, maddening to diagnose from behaviour.
    assertThrows<IllegalArgumentException> { registry.register(dto) }
  }

  @Test
  fun `a base priority override is carried through`() {
    // Built here rather than read off a shipped archetype: none of them override a base priority now that
    // passive_wanderer's `KillAttacker: 60` is gone, and that override was only ever readable as tuning
    // because fleeing was the real answer to being hurt. The mechanism still has to work, so it is tested
    // directly instead of through whichever profile happened to use it.
    val registry = AiProfileRegistry()
    registry.register(
      AiProfileDto(
        identifier = "tuned",
        goals = listOf(AiProfileDto.GoalDto("KillAttacker", basePriority = 60f)),
        actions = listOf("approachTarget", "attack"),
      )
    )

    val retaliation = registry.getOrThrow("tuned").goals.single { it.name == "KillAttacker" }
    assertEquals(60f, retaliation.basePriority)
  }

  @Test
  fun `no shipped archetype can flee`() {
    // The flee mechanic is gone from the domain, so a profile naming it would fail the boot outright. This
    // guards the profiles themselves: an archetype that listed `Flee` or a `flee` action could not load, and
    // finding that out at boot is worse than finding it out here.
    loadedRegistry().all().forEach { profile ->
      assertTrue(profile.goals.none { it.name == "Flee" }, "${profile.identifier} still lists a Flee goal")
      assertTrue("flee" !in profile.actionIds, "${profile.identifier} still lists a flee action")
    }
  }
}

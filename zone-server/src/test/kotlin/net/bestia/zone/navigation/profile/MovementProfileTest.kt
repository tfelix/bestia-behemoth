package net.bestia.zone.navigation.profile

import net.bestia.worldgen.core.MovementMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MovementProfileTest {

  @Test
  fun `the shipped profiles parse and say what they are meant to say`() {
    val registry = MovementProfileRegistry().apply { load() }

    val cart = assertNotNull(registry.get("merchant_cart"), "merchant_cart did not load")
    val wolf = assertNotNull(registry.get("wild_ground_forager"), "wild_ground_forager did not load")

    // The requirement, read straight off the content: one prefers the fast network and one avoids it.
    assertTrue(cart.roadCostMultiplier < 1.0, "a merchant should prefer roads")
    assertTrue(wolf.roadCostMultiplier > 1.0, "a wild animal should avoid roads")
    assertTrue(cart.agentHalfWidth > wolf.agentHalfWidth, "a cart is wider than a forager")
    assertFalse(MovementMode.CLIMB in cart.capabilities, "a cart should not be a mountaineer")
  }

  @Test
  fun `an unconfigured creature gets a walker rather than nothing`() {
    val registry = MovementProfileRegistry().apply { load() }

    val default = registry.getOrDefault(null)
    assertEquals(MovementProfile.DEFAULT_IDENTIFIER, default.identifier)
    assertTrue(MovementMode.WALK in default.capabilities)
  }

  @Test
  fun `an unknown profile falls back instead of throwing`() {
    // Deliberately unlike AiProfileRegistry.getOrThrow: a creature with no behaviour is worth refusing to boot
    // over, a creature that walks like everything else is worth a warning and a moving NPC.
    val registry = MovementProfileRegistry().apply { load() }

    assertEquals(MovementProfile.DEFAULT_IDENTIFIER, registry.getOrDefault("no_such_profile").identifier)
  }

  @Test
  fun `capabilities are required as a set, not any-of`() {
    val walker = MovementProfile("walker", setOf(MovementMode.WALK), 0.5, 1.0, 1.0)
    val swimmer = MovementProfile("swimmer", setOf(MovementMode.WALK, MovementMode.SWIM), 0.5, 1.0, 1.0)

    val ford = setOf(MovementMode.WALK, MovementMode.SWIM)

    assertFalse(walker.canTraverse(ford, Double.MAX_VALUE), "WALK alone must not satisfy a ford")
    assertTrue(swimmer.canTraverse(ford, Double.MAX_VALUE))
  }

  @Test
  fun `width is compared against the crossing's own limit`() {
    val cart = MovementProfile("cart", setOf(MovementMode.WALK), agentHalfWidth = 1.5, 1.0, 1.0)

    assertTrue(cart.canTraverse(setOf(MovementMode.WALK), maxAgentHalfWidth = 2.0))
    assertFalse(cart.canTraverse(setOf(MovementMode.WALK), maxAgentHalfWidth = 1.0))
  }

  @Test
  fun `a profile that cannot walk is refused at construction`() {
    // Every land edge in the generated graph demands WALK, so such a profile could never travel at all -
    // better a loud failure at boot than a creature that silently never moves.
    assertFailsWith<IllegalArgumentException> {
      MovementProfile("swimmer_only", setOf(MovementMode.SWIM), 0.5, 1.0, 1.0)
    }
  }

  @Test
  fun `a non-positive cost multiplier is refused`() {
    assertFailsWith<IllegalArgumentException> {
      MovementProfile("free_roads", setOf(MovementMode.WALK), 0.5, roadCostMultiplier = 0.0, 1.0)
    }
  }
}

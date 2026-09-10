package net.bestia.zone.ai.core.state

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * That a domain re-exports the shared keys rather than redeclaring them.
 *
 * Keys are equal by name, so a redeclaration would still address the same slot and everything would appear
 * to work - right up until the two disagreed about metadata. A second declaration missing
 * [StateKey.observed] lets the planner write an imagined observation into live memory, and one with the
 * wrong type parameter turns [WorldState]'s unchecked cast into a crash at whichever reader is unluckiest.
 * Neither shows up as a compile error, so it is checked here instead.
 */
class CommonKeyAliasTest {

  @Test
  fun `every shared key the bestia domain names is the shared key itself`() {
    val common = keysOf(CommonKeys).associateBy { it.name }
    val shared = keysOf(BestiaDomain).filter { it.name in common }

    assertTrue(shared.isNotEmpty(), "the bestia domain names none of the shared keys, so nothing is checked")

    for (key in shared) {
      assertSame(
        common.getValue(key.name),
        key,
        "BestiaDomain.${key.name} is a second declaration of a shared key rather than an alias of it"
      )
    }
  }

  @Test
  fun `no domain key shadows a shared key under a different declaration`() {
    val common = keysOf(CommonKeys).associateBy { it.name }

    for (key in keysOf(BestiaDomain)) {
      val shared = common[key.name] ?: continue
      assertEquals(shared.observed, key.observed, "${key.name} disagrees about being an observation")
      assertEquals(shared.scope, key.scope, "${key.name} disagrees about how far it propagates")
      assertEquals(shared.retain, key.retain, "${key.name} disagrees about how long it is kept")
    }
  }

  private fun keysOf(owner: Any): List<StateKey<*>> {
    return owner::class.declaredMemberProperties
      .mapNotNull { property ->
        property.isAccessible = true
        runCatching { property.getter.call(owner) }.getOrNull() as? StateKey<*>
      }
  }
}

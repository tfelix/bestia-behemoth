package net.bestia.zone.ai.profile

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import net.bestia.zone.ai.domain.allAiDomains

/**
 * That a profile's `domain:` is checked, and checked without a Spring context.
 *
 * The second half is the one worth guarding. Validation lives on a registry with no constructor arguments,
 * and the obvious way to let a profile choose a domain - inject the thing that builds agents - would pull a
 * navigation service and both attack pathways behind a parse. [AiProfileRegistryTest] builds the registry
 * bare and is deliberately untouched by the seam; this file constructs it the same way.
 */
class UnknownDomainTest {

  @Test
  fun `a profile naming a domain that does not exist fails fast, and says what does`() {
    val error = assertThrows<IllegalArgumentException> {
      AiProfileRegistry(allAiDomains()).register(profile(domain = "nonsense"))
    }

    assertTrue(error.message!!.contains("nonsense"), "the message must name the domain that was asked for")
    assertTrue(
      error.message!!.contains(BestiaDomain.ID),
      "and the ones that exist, or the author has nothing to correct it to: ${error.message}"
    )
  }

  @Test
  fun `a profile that says nothing about a domain gets the wild creatures`() {
    // The compatibility default the shipped archetypes rely on: none of them carries a `domain:` key.
    assertEquals(BestiaDomain.ID, AiProfileRegistry(allAiDomains()).register(profile()).domain)
  }

  @Test
  fun `every registered domain answers to its own id`() {
    // A catalogue filed under a different key than it reports would make the lookup above unreachable for
    // exactly one domain, and nothing else would notice.
    val domains = allAiDomains()
    for (id in domains.ids) {
      assertEquals(id, domains.of(id)?.id)
    }
  }

  private fun profile(domain: String? = null): AiProfileDto {
    val goals = listOf(AiProfileDto.GoalDto("Sleep"))
    val actions = listOf("sleep")

    return domain
      ?.let { AiProfileDto(identifier = "under-test", domain = it, goals = goals, actions = actions) }
      ?: AiProfileDto(identifier = "under-test", goals = goals, actions = actions)
  }
}

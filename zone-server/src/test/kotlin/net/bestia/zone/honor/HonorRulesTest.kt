package net.bestia.zone.honor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HonorRulesTest {

  @Test
  fun `the shipped file prices every act`() {
    val rules = HonorCatalog().rules

    assertEquals(HonorRules.Amount(global = -10, local = -60), rules.amountOf(HonorAct.KILL_NPC))
    assertEquals(20, rules.global.start)
  }

  @Test
  fun `a file that leaves an act out is refused`() {
    val partial = """
      acts:
        KILL_NPC: { global: -10, local: -60 }
      protected-zone: { loss-factor: 2.0, gain-factor: 1.5 }
      witness-range: 30
      gossip: { delay-hours: 8, share: 0.5, rumour-loss: 30 }
      layers:
        global: { start: 20, cap: 100, recovery-per-day: 1, grace-days: 1 }
        settlement: { start: 10, cap: 100, recovery-per-day: 2, grace-days: 1 }
        grudge: { start: 0, cap: 50, recovery-per-day: 2, grace-days: 1 }
      thresholds: { unfriendly-below: 0, hostile-below: -60 }
    """.trimIndent()

    assertFailsWith<IllegalArgumentException> { HonorRules.read(partial.byteInputStream()) }
  }
}

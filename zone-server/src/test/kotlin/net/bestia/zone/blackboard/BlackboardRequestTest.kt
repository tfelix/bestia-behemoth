package net.bestia.zone.blackboard

import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlackboardRequestTest {

  @Test
  fun `a plain click opens the board`() {
    assertEquals(BlackboardRequest.Open, BlackboardRequest.parse(ScriptArgs.EMPTY))
  }

  @Test
  fun `a posting carries what, how many, for how much and how long`() {
    val args = ScriptArgs.of(
      ScriptArgKeys.ACTION to "post", ScriptArgKeys.ITEM_ID to 8L, ScriptArgKeys.AMOUNT to 40L,
      ScriptArgKeys.COINS_EACH to 12L, ScriptArgKeys.DAYS to 3L,
    )

    assertEquals(BlackboardRequest.Post(itemId = 8, amount = 40, coinsEach = 12, days = 3), BlackboardRequest.parse(args))
  }

  @Test
  fun `signing and handing in name the quest`() {
    val sign = ScriptArgs.of(ScriptArgKeys.ACTION to "sign", ScriptArgKeys.QUEST_ID to 7L)
    val handIn = ScriptArgs.of(ScriptArgKeys.ACTION to "handIn", ScriptArgKeys.QUEST_ID to 7L, ScriptArgKeys.AMOUNT to 4L)

    assertEquals(BlackboardRequest.Sign(7), BlackboardRequest.parse(sign))
    assertEquals(BlackboardRequest.HandIn(7, 4), BlackboardRequest.parse(handIn))
    assertNull(BlackboardRequest.parse(ScriptArgs.of(ScriptArgKeys.ACTION to "handIn", ScriptArgKeys.QUEST_ID to 7L)))
  }

  @Test
  fun `a bag no client builds is refused`() {
    assertNull(BlackboardRequest.parse(ScriptArgs.of(ScriptArgKeys.ACTION to "post", ScriptArgKeys.ITEM_ID to 8L)))
    assertNull(BlackboardRequest.parse(ScriptArgs.of(ScriptArgKeys.ACTION to "burn")))
    assertNull(BlackboardRequest.parse(ScriptArgs.of(ScriptArgKeys.ACTION to "withdraw")))
  }
}

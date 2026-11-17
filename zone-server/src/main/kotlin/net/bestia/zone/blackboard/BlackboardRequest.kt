package net.bestia.zone.blackboard

import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs

/** What a click on a board asks for, read from its [ScriptArgs] bag. */
sealed interface BlackboardRequest {

  data object Open : BlackboardRequest

  data class Post(val itemId: Long, val amount: Int, val coinsEach: Long, val days: Int) : BlackboardRequest

  data class Withdraw(val questId: Long) : BlackboardRequest

  data class Sign(val questId: Long) : BlackboardRequest

  data class HandIn(val questId: Long, val amount: Int) : BlackboardRequest

  companion object {
    /** @return null for a bag no honest client builds */
    fun parse(args: ScriptArgs): BlackboardRequest? {
      return when (args.text(ScriptArgKeys.ACTION)) {
        null -> Open
        "post" -> post(args)
        "withdraw" -> args.long(ScriptArgKeys.QUEST_ID)?.let { Withdraw(it) }
        "sign" -> args.long(ScriptArgKeys.QUEST_ID)?.let { Sign(it) }
        "handIn" -> handIn(args)
        else -> null
      }
    }

    private fun handIn(args: ScriptArgs): HandIn? {
      val questId = args.long(ScriptArgKeys.QUEST_ID) ?: return null
      val amount = args.long(ScriptArgKeys.AMOUNT)?.takeIf { it in 1..Int.MAX_VALUE.toLong() } ?: return null

      return HandIn(questId, amount.toInt())
    }

    private fun post(args: ScriptArgs): Post? {
      val itemId = args.long(ScriptArgKeys.ITEM_ID) ?: return null
      val amount = args.long(ScriptArgKeys.AMOUNT) ?: return null
      val coinsEach = args.long(ScriptArgKeys.COINS_EACH) ?: return null
      val days = args.long(ScriptArgKeys.DAYS) ?: return null
      if (amount !in 1..Int.MAX_VALUE.toLong() || days !in 1..Int.MAX_VALUE.toLong()) {
        return null
      }

      return Post(itemId, amount.toInt(), coinsEach, days.toInt())
    }
  }
}

package net.bestia.zone.dialog.conversation

/**
 * The translation keys conversation itself owns, as opposed to the ones a memory brings with it.
 *
 * Named here rather than written at each call site so that the boot check has one list to read and a
 * misspelling is a compile error rather than a raw key shown to a player.
 */
object ConversationKeys {

  /**
   * A prefix, not a key: the occupation and the variant follow, as `TALK_GREETING_GUARD_2`.
   *
   * Per trade because the opening line is the one every conversation shows, so it is where sameness is
   * felt first - and per variant because two guards in one town greeting a player identically is the
   * same complaint one step further in.
   */
  const val GREETING_PREFIX = "TALK_GREETING_"

  /**
   * The rows a player clicks, which have no variants on purpose.
   *
   * A pinned option is the part of a conversation a player is meant to be able to rely on - see
   * `ConversationService.rootOptions` - and an option that is worded differently by each person you
   * meet is one nobody learns to look for. The variance belongs to the answers, which is where being
   * told the same thing twice is actually felt.
   */
  const val FAREWELL = "TALK_FAREWELL"
  const val BACK = "TALK_BACK"
  const val ABOUT_TOWN_ASK = "TALK_ABOUT_TOWN_ASK"
  const val NEWS_ASK = "TALK_NEWS_ASK"

  /**
   * And the answers, each a *prefix* like [GREETING_PREFIX]: the variant follows, as `TALK_GOODBYE_2`.
   *
   * How many each has is `dialogue.yml`'s to say, read by [ConversationLineCatalogue], which is the
   * only thing that should ever build one of these keys.
   */
  const val GOODBYE = "TALK_GOODBYE"
  const val ABOUT_TOWN = "TALK_ABOUT_TOWN"
  const val NEWS_NONE = "TALK_NEWS_NONE"

  const val TRADE_ASK = "TALK_TRADE_ASK"

  /** A prefix, as [GREETING_PREFIX] is: `TALK_TRADE_LABOURER_1`. */
  const val TRADE_LINE_PREFIX = "TALK_TRADE_"

  const val SHOP_BUY_ASK = "TALK_SHOP_BUY_ASK"
  const val SHOP_SELL_ASK = "TALK_SHOP_SELL_ASK"

  /** Said as the window opens, so the conversation does not simply stop dead on an action. */
  const val SHOP_OPENED = "TALK_SHOP_OPENED"

  /** What a stubbed action answers with. One key, so removing it later is one grep. */
  const val NOT_YET = "TALK_NOT_YET"

  /**
   * Every key whose phrasings are counted in `dialogue.yml`, so that the boot can check the two agree.
   *
   * A list rather than a convention over the constants above, because half of them are deliberately
   * single - a key added here with no count fails the boot, and a count with no key does too.
   */
  val VARIED = setOf(GOODBYE, ABOUT_TOWN, NEWS_NONE, NOT_YET, SHOP_OPENED)

  const val SLOT_NAME = "name"
  const val SLOT_TOWN = "town"
  const val SLOT_TRADE = "trade"
}

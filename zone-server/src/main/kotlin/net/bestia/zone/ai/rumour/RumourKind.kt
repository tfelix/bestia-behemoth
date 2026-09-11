package net.bestia.zone.ai.rumour

/**
 * A thing that happened in play recently enough that nobody has written it down.
 *
 * The chronicle covers everything up to the present year and nothing after it, so this enum is the
 * short list of things a town can be talking about that the seed cannot produce.
 *
 * **A kind belongs here only once something posts it.** A kind with no producer is a phrasing nobody
 * can ever reach, which is the same mistake as an event class no townsperson can mention - and the more
 * expensive half of it, because a translator is paid for the line either way.
 */
enum class RumourKind(
  /** 0 to 100, on `EventKind.baseImportance`'s scale, so a rumour and a chronicle memory compare. */
  val baseImportance: Int,
  /** How many days before nobody brings it up any more. */
  val lifetimeDays: Int,
) {

  /** Something large killed close enough to the walls that people heard about it. */
  BOSS_SLAIN(baseImportance = 55, lifetimeDays = 20),
}

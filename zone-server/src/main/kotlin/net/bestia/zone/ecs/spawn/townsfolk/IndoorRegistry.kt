package net.bestia.zone.ecs.spawn.townsfolk

import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Who is indoors, at which door, and until when.
 *
 * Going inside is how a townsperson stops existing. There are no interiors, so a person who walks through
 * their own front door is destroyed and remembered here instead - which costs a map entry rather than an
 * entity, an agent, a plan and a place in every AI wave.
 *
 * The mechanism is deliberately the same one for both reasons somebody is out of sight: a shopkeeper
 * between customers and a whole town asleep. That is worth choosing on purpose, because it is the option
 * that becomes *correct* rather than obsolete once interiors exist - the person really is inside the
 * building, and the day interiors arrive, what changes is that they are drawn there.
 *
 * Not persisted. Everything in here is derivable from the settlement's seed and the clock, and a row per
 * sleeping villager is the thing this whole layer exists not to have.
 */
@Service
class IndoorRegistry {

  /**
   * @param door where they will step back out, in position units
   * @param until the hours they stay in for - a rest window, or a shift. They emerge when the clock leaves it.
   */
  class Indoors(
    val identity: Long,
    val door: Vec3L,
    val until: HourWindow,
  )

  // Concurrent because chat commands and tooling read it off the tick thread while systems write it.
  private val byIdentity = ConcurrentHashMap<Long, Indoors>()

  val size: Int get() = byIdentity.size

  fun enter(identity: Long, door: Vec3L, until: HourWindow) {
    byIdentity[identity] = Indoors(identity, door, until)
  }

  fun isIndoors(identity: Long): Boolean {
    return byIdentity.containsKey(identity)
  }

  /**
   * Everybody whose reason to be inside has passed.
   *
   * Against the clock rather than a countdown, for [net.bestia.zone.ai.bt.UntilHour]'s reason: nothing
   * about an indoor record is ticked, so a duration would have to be counted by somebody, and the somebody
   * would have to still be running while the town is empty.
   */
  fun dueOut(hour: Int): List<Indoors> {
    return byIdentity.values.filterNot { it.until.covers(hour) }
  }

  fun leave(identity: Long): Indoors? {
    return byIdentity.remove(identity)
  }

  /** Forgets a whole settlement's indoor people, for when the town is torn down behind a departing player. */
  fun forgetSettlement(settlement: Int) {
    byIdentity.keys.removeIf { TownsfolkIdentity.settlementOf(it) == settlement }
  }
}

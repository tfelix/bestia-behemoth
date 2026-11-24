package net.bestia.zone.account.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.Embeddable
import jakarta.persistence.OneToMany
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.account.BestiaPlace
import net.bestia.zone.account.PlayerBestiaPolicy
import java.time.Instant

@Embeddable
class MasterBestias {

  @OneToMany(mappedBy = "master", cascade = [CascadeType.ALL])
  private val bestias: MutableSet<PlayerBestia> = mutableSetOf()

  val ownedBestias: List<PlayerBestia> get() = bestias.toList()

  /** The ones out in the world, each taking an active slot. */
  val activeBestias: List<PlayerBestia>
    get() {
      return bestias.filterNot { it.isStored }
    }

  val storedBestias: List<PlayerBestia>
    get() {
      return bestias.filter { it.isStored }
    }

  fun addBestia(master: Master, bestia: Bestia, bestiaPolicy: PlayerBestiaPolicy, now: Instant): PlayerBestia {
    val place = bestiaPolicy.placeFor(master)

    val pb = PlayerBestia(master, bestia)
    if (place == BestiaPlace.STORAGE) {
      pb.storedAt = now
    }
    bestias.add(pb)

    return pb
  }
}
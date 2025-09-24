package net.bestia.zone.account.master

import jakarta.persistence.CascadeType
import jakarta.persistence.Embeddable
import jakarta.persistence.OneToMany
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.bestia.PlayerBestia

@Embeddable
class MasterBestias {

  @OneToMany(mappedBy = "master", cascade = [CascadeType.ALL])
  private val bestias: MutableSet<PlayerBestia> = mutableSetOf()

  val ownedBestias: List<PlayerBestia> get() = bestias.toList()

  fun addBestia(master: Master, bestia: Bestia, bestiaPolicy: PlayerBestiaPolicy): PlayerBestia {
    bestiaPolicy.checkPolicy(master, bestia)

    val pb = PlayerBestia(master, bestia)
    bestias.add(pb)

    return pb
  }
}
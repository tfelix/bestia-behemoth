package net.bestia.zone.faction.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import net.bestia.worldgen.core.Faction

/** The Novitiate a master walks, at most one at a time: the Order, its quest, and the town it began in. */
@Entity
@Table(name = "faction_novitiate", indexes = [Index(columnList = "quest_id", unique = true)])
class NovitiateRecord(
  @Id
  @Column(name = "master_id")
  val masterId: Long,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  val faction: Faction,

  @Column(name = "quest_id", nullable = false)
  val questId: Long,

  @Column(name = "home_settlement", nullable = false)
  val homeSettlement: Int,
)

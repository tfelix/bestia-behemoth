package net.bestia.zone.item.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.bestia.zone.item.container.ContainerOwner

/**
 * A guild's one storage, opened at any of its stewards. Keyed by the guild alone: it outlives every Stronghold and
 * every world, as the guild does. Here rather than with the guild, so the item slice can lock it like a chest.
 */
@Entity
@Table(name = "guild_storage")
class GuildStorage(
  @Id
  @Column(name = "guild_id", nullable = false)
  val guildId: Long = 0,

  @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true, optional = false)
  @JoinColumn(name = "container_id", nullable = false)
  override val container: ItemContainer = ItemContainer(ItemContainer.Type.STORAGE),
) : ContainerOwner

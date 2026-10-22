package net.bestia.zone.guild.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * A guild's emblem, a PNG already cleaned by `EmblemImage`. Apart from [Guild] so reading a guild never loads it;
 * the api-service asks for it by [hash], which [Guild.emblemHash] mirrors.
 */
@Entity
@Table(name = "guild_emblem")
class GuildEmblem(
  @Id
  @Column(name = "guild_id")
  val guildId: Long,

  @Column(nullable = false, length = 64)
  var hash: String,

  @Column(nullable = false, length = 8192)
  var png: ByteArray,
)

package net.bestia.zone.guild.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface GuildEmblemRepository : JpaRepository<GuildEmblem, Long> {

  /** Two guilds may have uploaded the same picture; either copy will do. */
  fun findFirstByHash(hash: String): GuildEmblem?
}

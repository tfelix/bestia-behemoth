package net.bestia.model.dao

import net.bestia.model.domain.GuildMember
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface GuildMemberDAO : CrudRepository<GuildMember, Int> {
  fun findByPlayerBestiaId(playerBestiaId: Long): GuildMember?
}

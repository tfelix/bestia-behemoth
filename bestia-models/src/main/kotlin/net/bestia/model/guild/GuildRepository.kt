package net.bestia.model.guild

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface GuildRepository : CrudRepository<Guild, Int>
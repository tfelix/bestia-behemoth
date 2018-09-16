package net.bestia.model.dao;

import net.bestia.model.domain.GuildRank;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildRankDAO extends CrudRepository<GuildRank, Integer> { }

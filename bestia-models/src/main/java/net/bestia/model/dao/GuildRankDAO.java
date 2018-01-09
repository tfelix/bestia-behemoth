package net.bestia.model.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import net.bestia.model.domain.GuildRank;

@Repository
public interface GuildRankDAO extends CrudRepository<GuildRank, Integer> { }

package net.bestia.model.dao;

import org.springframework.stereotype.Repository;

import net.bestia.model.domain.GuildRank;

@Repository
public interface GuildRankDAO extends CrudOptionalRepository<GuildRank, Integer> { }

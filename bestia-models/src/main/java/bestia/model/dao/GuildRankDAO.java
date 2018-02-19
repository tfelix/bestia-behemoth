package bestia.model.dao;

import org.springframework.stereotype.Repository;

import bestia.model.domain.GuildRank;

@Repository
public interface GuildRankDAO extends CrudOptionalRepository<GuildRank, Integer> { }

package net.bestia.model.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import net.bestia.model.domain.GuildMember;

@Repository
public interface GuildMemberDAO extends CrudRepository<GuildMember, Integer> {
	
	GuildMember findByPlayerBestiaId(long playerBestiaId);
}

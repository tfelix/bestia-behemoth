package bestia.model.dao;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import bestia.model.domain.GuildMember;

@Repository
public interface GuildMemberDAO extends CrudOptionalRepository<GuildMember, Integer> {
	
	Optional<GuildMember> findByPlayerBestiaId(long playerBestiaId);
}

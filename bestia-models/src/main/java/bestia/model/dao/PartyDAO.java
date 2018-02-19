package bestia.model.dao;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import bestia.model.domain.Party;

@Repository("partyDao")
public interface PartyDAO extends CrudRepository<Party, Long> {

	/**
	 * Finds the party in which the given account is a member.
	 * 
	 * @param accountId
	 *            The party which contains this account.
	 * @return The party or null if the account is no member in any party.
	 */
	@Query("SELECT pr FROM Party pr JOIN pr.members p WHERE accountId = p.id")
	public Party findPartyByMembership(long accountId);

}

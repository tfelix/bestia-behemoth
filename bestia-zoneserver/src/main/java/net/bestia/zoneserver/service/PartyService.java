package net.bestia.zoneserver.service;

import org.springframework.stereotype.Service;

import net.bestia.model.dao.PartyDAO;

/**
 * Service to manage the parties of the players. Parties are a way to organize
 * loose groups of players. A party is permanent in such a way it is persisted
 * upon the server until the last member leaves it.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class PartyService {

	//private final PartyDAO partyDao;

	public PartyService(PartyDAO partyDao) {

		//this.partyDao = Objects.requireNonNull(partyDao);
	}

	/**
	 * Adds a new member to this party. All party member can invite new member
	 * to a party. The player must not be a member of another party. He must
	 * leave the other party first before he can be added.
	 * 
	 * @param accountId
	 * @return TRUE if the member could be added. FALSE otherwise.
	 */
	public boolean addPartyMember(long memberAccountId, long invitedAccountId) {
		throw new IllegalStateException("Method not implemented.");
	}

	/**
	 * Removes the member from the party. If a party owner (or party admin)
	 * leaves a party a new party member is promoted to the owner state. If the
	 * last member (and thus last owner) has left the party the party is destroyed
	 * and removed from the database.
	 * 
	 * @param accountId
	 *            The account id to be removed from a party.
	 * @param partyName
	 *            The name of the party.
	 */
	public void removePartyMember(long accountId, String partyName) {
		throw new IllegalStateException("Method not implemented.");
	}
}

package bestia.model.dao;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import bestia.model.domain.PlayerItem;

@Repository
public interface PlayerItemDAO extends CrudRepository<PlayerItem, Integer> {

	/**
	 * Returns all PlayerItems for a particular account id.
	 * 
	 * @param accId
	 *            All items of this account are found.
	 * @return A set of the player items.
	 */
	@Query("SELECT pi FROM PlayerItem pi WHERE pi.account.id = :accId")
	public List<PlayerItem> findPlayerItemsForAccount(@Param("accId") long accId);

	/**
	 * Searches if a given account has a particular item. If found it returns
	 * the item null otherwise.
	 * 
	 * @param accId
	 *            Account ID.
	 * @param itemId
	 *            The item id.
	 * @return The found PlayerItem, null otherwise.
	 */
	@Query("SELECT pi FROM PlayerItem pi where pi.account.id = :accId and pi.item.id = :itemId")
	public PlayerItem findPlayerItem(@Param("accId") long accId, @Param("itemId") int itemId);

	/**
	 * Returns the total weight of all items inside the inventory of the given
	 * account.
	 * 
	 * @param accId
	 *            Account ID
	 * @return The summed weight of all items.
	 */
	@Query("SELECT sum(item.weight * pi.amount) from PlayerItem pi WHERE pi.account.id = :accId")
	public int getTotalItemWeight(@Param("accId") long accId);

	/**
	 * Returns a list of all player items whose id is in the set given as
	 * parameter. The id list are item IDs.
	 * 
	 * @param ids
	 *            Set of item ids.
	 * @return The found player items with these item IDs.
	 */
	@Query("SELECT pi FROM PlayerItem pi WHERE pi.id IN (:idList)")
	public List<PlayerItem> findAllPlayerItemsForIds(@Param("idList") Set<Integer> itemIds);

	/**
	 * Returns the number of items owned by the player.
	 * 
	 * @return The number of items owned by this account id.
	 */
	@Query("SELECT COUNT(pi) FROM PlayerItem pi WHERE pi.account.id = :accId")
	public int countPlayerItemsForAccount(@Param("accId") long accId);
}

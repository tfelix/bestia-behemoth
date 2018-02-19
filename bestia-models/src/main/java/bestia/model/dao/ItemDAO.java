package bestia.model.dao;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import bestia.model.domain.Item;

@Repository
public interface ItemDAO extends CrudRepository<Item, Integer> {

	/**
	 * Returns an item by its item database name. The name is unique. Returns null if the item was not found.
	 * 
	 * @param itemDbName
	 *            Unique database name of an item.
	 * @return The found item. Or null if the item was not found.
	 */
	@Query("SELECT i FROM Item i where i.itemDbName = :name")
	public Item findItemByName(@Param("name") String itemDbName);
}

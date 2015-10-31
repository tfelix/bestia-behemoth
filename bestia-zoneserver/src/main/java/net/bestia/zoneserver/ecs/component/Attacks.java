package net.bestia.zoneserver.ecs.component;

import java.util.ArrayList;
import java.util.List;

import com.artemis.Component;

/**
 * Holding this class allows the entities to perform attacks which will
 * basically spawn entities. The component needs to hold all attack IDs which a
 * entity can perform. If the ID is not listed inside here the attack will not
 * be possible.
 * <p>
 * The attacks are implemented as a array because the order is somehow important
 * (attacks <-> slots). A HashSet would not map this really good. Since we do
 * lookups this can result in a not so optimal performance. However we only deal
 * with 5-6 attacks at most so we should be fine.
 * </p>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Attacks extends Component {

	private final List<Integer> attackIds = new ArrayList<>();

	public Attacks() {

	}

	public void clear() {
		attackIds.clear();
	}

	/**
	 * Adds the given ID to the set of allowed attacks in this component.
	 * 
	 * @param id
	 *            Attack ID to add to this list.
	 */
	public void add(Integer id) {
		attackIds.add(id);
	}

	/**
	 * Checks if a certain attack ID can performed by this entity.
	 * 
	 * @param id
	 *            The ID of the attack to check ownership.
	 * @return TRUE if this entity knows and can use this attack. FALSE
	 *         otherwise.
	 */
	public boolean hasAttack(Integer id) {
		return attackIds.contains(id);
	}

}

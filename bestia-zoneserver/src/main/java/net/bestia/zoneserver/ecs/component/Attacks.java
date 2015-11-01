package net.bestia.zoneserver.ecs.component;

import java.util.Collection;

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
	
	private static final int NUM_ATTACKS = 5;

	private final Integer[] attackIds = new Integer[NUM_ATTACKS];

	public Attacks() {

	}

	public void clear() {
		for(int i = 0; i < NUM_ATTACKS; i++) {
			attackIds[i] = null;
		}
	}
	
	public Integer[] getAttacks() {
		return attackIds;
	}

	/**
	 * Adds the given ID to the set of allowed attacks in this component.
	 * 
	 * @param id
	 *            Attack ID to add to this list.
	 */
	public void add(int slot, Integer id) {
		if(slot < 0 || slot >= NUM_ATTACKS) {
			throw new IllegalArgumentException("Slot must be between 0 and "+ NUM_ATTACKS);
		}
		
		attackIds[slot] = id;
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
		for(int i = 0; i < NUM_ATTACKS; i++) {
			if(attackIds[i] == id) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Adds all the given attack IDs to the component.
	 * 
	 * @param atkIds
	 */
	public void addAll(Collection<Integer> atkIds) {
		if(atkIds.size() > NUM_ATTACKS) {
			throw new IllegalArgumentException("Number of attacks can not exceed " + NUM_ATTACKS);
		}
		
		int i = 0;
		for(Integer id : atkIds) {
			attackIds[i] = id;
			i++;
		}
	}

}

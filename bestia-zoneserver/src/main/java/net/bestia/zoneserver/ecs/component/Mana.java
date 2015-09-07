package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

/**
 * Adds a regenerating mana pool to a entity. The current mana and max mana must be synced with the entities in the
 * background.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Mana extends Component {

	public int maxMana;
	public int curMana;
	
}

package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

/**
 * Makes an entity visible and set a sprite to it. Note: Its no "sprite" in the ordinary sense. The sprite can be more
 * of a sprite system. It is described in the sprite description file which can be found under this name. The client
 * engine has to interpret this description and use the necessairy files.
 * 
 * @author Thomas
 *
 */
public class Visible extends Component {

	/**
	 * Flag if the entity has changed in some way so its state must be re-transmitted to the clients in sight.
	 */
	public boolean hasChanged;
	public String sprite;
}

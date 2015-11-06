package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

/**
 * Makes an entity visible and set a sprite to it. Note: Its no "sprite" in the
 * ordinary sense. The sprite can be more of a sprite system. It is described in
 * the sprite description file which can be found under this name. The client
 * engine has to interpret this description and use the necessairy files.
 * 
 * @author Thomas
 *
 */
public class Visible extends Component {

	public enum VisibleType {
		BESTIA,

		/**
		 * Items get a short "drop" animation when they appear and the player is
		 * able to click on them to interact via them.
		 */
		ITEM,

		/**
		 * The player can interact with the entity via clicking on it. The
		 * entity should handle such clicks via a script component.
		 */
		INTERACT,

		/**
		 * Generic entity. No special treatment in the engine. It will
		 * "just be displayed."
		 */
		GENERIC
	}

	public String sprite = "";
	public VisibleType type = VisibleType.GENERIC;

	public Visible() {

	}

	public Visible(String sprite) {
		this.sprite = sprite;
	}
}

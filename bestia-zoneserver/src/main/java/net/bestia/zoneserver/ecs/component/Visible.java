package net.bestia.zoneserver.ecs.component;

import java.util.ArrayList;
import java.util.List;

import com.artemis.Component;

/**
 * Makes an entity visible and set a sprite to it. Note: Its no "sprite" in the
 * ordinary sense. The sprite can be more of a sprite system. It is described in
 * the sprite description file which can be found under this name. The client
 * engine has to interpret this description and use the necessary files.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Visible extends Component {

	public enum SpriteType {
		SINGLE, PACK, ITEM
	}

	/**
	 * Maby these classifications of the entity type should not be done in the
	 * visible component but rather in an own, dedicated component. 
	 * 
	 * @author Thomas
	 *
	 */
	public enum InteractionType {
		/**
		 * Simple mob on the map. The player can interact/attack it.
		 */
		MOB,

		/**
		 * NPCs who is primerly friendly.
		 */
		NPC,

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

	public static class Sprite {
		public String name;
		public String animation;
		public SpriteType spriteType;
		public InteractionType interactionType;
	}

	public List<Sprite> sprites = new ArrayList<>();
	public String sprite = "";
	public InteractionType type = InteractionType.GENERIC;

	public Visible() {

	}

	public Visible(String sprite) {
		this.sprite = sprite;
	}
}

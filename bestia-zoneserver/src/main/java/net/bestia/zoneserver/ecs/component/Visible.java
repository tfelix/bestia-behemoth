package net.bestia.zoneserver.ecs.component;

import java.util.ArrayList;
import java.util.List;

import com.artemis.Component;

import net.bestia.messages.entity.EntityType;
import net.bestia.model.misc.Sprite;
import net.bestia.model.misc.Sprite.InteractionType;

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

	public List<Sprite> sprites = new ArrayList<>();
	public String sprite = "";
	public EntityType spriteType = EntityType.NONE;
	public InteractionType interactionType = InteractionType.GENERIC;

	public Visible() {

	}

	public Visible(String sprite) {
		this.sprite = sprite;
	}
}

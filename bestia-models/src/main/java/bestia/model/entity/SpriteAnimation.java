package bestia.model.entity;

import java.io.Serializable;

/**
 * Can be used to send to the client in order to playback a certain animation
 * from a sprite sheet.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class SpriteAnimation implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String animationName;

	public String getAnimationName() {
		return animationName;
	}

}

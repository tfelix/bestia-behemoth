package net.bestia.zoneserver.entity.message;

import net.bestia.messages.entity.EntityDescriptionMessage;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.zoneserver.entity.BaseEntity;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;

/**
 * This will create different kind of entity messages which can be send to the
 * client in order to perform certain entity (visual) actions.
 * 
 * @author Thomas Felix <thomas.felix@tfleix.de
 *
 */
public class MessageFactory {

	// TODO Das hier ggf. geschickter lösen, sodass neue Entities besser
	// gehandled werden können.
	public EntityDescriptionMessage createDescriptionMessage(BaseEntity e) {

		if(!(e instanceof PlayerBestiaEntity)) {
			return null;
		}
		
		final PlayerBestiaEntity pbe = (PlayerBestiaEntity) e;
		
		return new EntityDescriptionMessage(pbe.getAccountId(), pbe.getId(), pbe.getVisual());
	}
	
	public EntityMoveMessage createMoveMessage() {
		return null;
	}

}

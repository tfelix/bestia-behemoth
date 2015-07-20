package net.bestia.messages;

/**
 * This message is send to the clients whenever an entity changes during the calculation of the zone system. Changes in
 * movement, animation etc are send to the client. However not all animation changes for example are propagated to the
 * client. Its up to him to decide which animation to play (dmg upon receiving of damage vor example).
 * 
 * @author Thomas
 *
 */
public class EntityUpdateMessage extends Message {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.update";
	
	private int playerBestiaId;
	private int x;
	private int y;
	
	public EntityUpdateMessage() {
		
	}
	
	public EntityUpdateMessage(long accId, int pbid, int x, int y) {
		this.playerBestiaId = pbid;
		this.x = x;
		this.y = y;
		
		setAccountId(accId);
	}
	
	

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

}

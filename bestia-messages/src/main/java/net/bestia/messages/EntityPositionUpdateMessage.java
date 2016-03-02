package net.bestia.messages;

/**
 * This message is send to the clients whenever an entity changes during the calculation of the zone system. Changes in
 * movement, animation etc are send to the client. However not all animation changes for example are propagated to the
 * client. Its up to him to decide which animation to play (a damage animation for example).
 * 
 * @author Thomas
 *
 */
public class EntityPositionUpdateMessage extends Message {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.update";

	private int x;
	private int y;
	private String entityId;

	public EntityPositionUpdateMessage() {

	}

	public EntityPositionUpdateMessage(String entityId, long accId, int pbid, int x, int y) {
		this.setX(x);
		this.setY(y);
		this.setEntityId(entityId);

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

	public String getEntityId() {
		return entityId;
	}

	public void setEntityId(String entityId) {
		this.entityId = entityId;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	@Override
	public String toString() {
		return String.format("EntityUpdateMessage[uuid: %s, accId: %d, x: %d, y: %d]", entityId, getAccountId(), x, y);
	}
}

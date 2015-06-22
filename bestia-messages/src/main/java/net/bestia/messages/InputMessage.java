package net.bestia.messages;

/**
 * Special class for player inputs which should generate a real server command. Instead the input is directed into the
 * EC system of the zone which is responsible for the given player bestia. Some sanity checks are performed like if the
 * account is really the owner of the bestia wished to control. Other than that the ECS is responsible for performing
 * the actions.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class InputMessage extends Message {

	private static final long serialVersionUID = 1L;

	@Override
	public String getMessageId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getMessagePath() {
		// TODO Auto-generated method stub
		return null;
	}

}

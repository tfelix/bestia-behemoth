package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

import net.bestia.messages.ChatMessage;

/**
 * Chat message entity. Must be used to public chat. ChatSystem will use this.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Chat extends Component {

	public ChatMessage chatMessage;

}

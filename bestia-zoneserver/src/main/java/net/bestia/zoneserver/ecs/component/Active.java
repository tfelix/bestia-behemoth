package net.bestia.zoneserver.ecs.component;

import java.util.LinkedList;
import java.util.Queue;

import net.bestia.messages.ChatMessage;

import com.artemis.Component;

/**
 * Entity is activly controlled currently by the player.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Active extends Component {

	public Queue<ChatMessage> chatQueue = new LinkedList<>();
}

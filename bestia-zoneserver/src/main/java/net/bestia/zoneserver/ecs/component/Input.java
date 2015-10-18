package net.bestia.zoneserver.ecs.component;

import net.bestia.messages.InputMessage;

import com.artemis.Component;

/**
 * Component can be used th feed {@link InputMessage}s into the ECS. The
 * InputCommand system will take care of all input entities and execute the
 * commands for this messages.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Input extends Component {

	public InputMessage inputMessage;

	public Input() {

	}

	public Input(InputMessage msg) {
		this.inputMessage = msg;
	}

}

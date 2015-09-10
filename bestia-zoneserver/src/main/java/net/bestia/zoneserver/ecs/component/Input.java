package net.bestia.zoneserver.ecs.component;

import net.bestia.messages.InputMessage;

import com.artemis.Component;

/**
 * TODO Kommentieren.
 * @author Thomas
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

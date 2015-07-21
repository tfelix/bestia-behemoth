package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

public class Changable extends Component {
	
	public boolean changed = false;
	
	public Changable() {
		// no op.
	}
	
	public Changable(boolean changed) {
		this.changed = changed;
	}

}

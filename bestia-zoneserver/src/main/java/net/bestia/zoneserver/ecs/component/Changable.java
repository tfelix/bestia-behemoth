package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

public class Changable extends Component {
	
	public boolean changed = false;
	public boolean hasPersisted = true;
	
	public Changable() {
		// no op.
	}
	
	public Changable(boolean changed) {
		this.changed = changed;
	}
	
	public void setChanged() {
		changed = true;
		hasPersisted = false;
	}

}

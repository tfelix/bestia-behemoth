package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

/**
 * Component to hold a delay after which it shall be executed.
 * 
 * Das ist eine doofe Komponent. Zu unaussagekräftig.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Delay extends Component {

	private int delay;
	private float timer;
	
	public void setDelay(int delay) {
		this.delay = delay;
		this.timer = delay;
	}
	
	public float getTimer() {
		return timer;
	}
	
	public int getDelay() {
		return delay;
	}
	
	public void setTimer(float timer) {
		this.timer = timer;
	}
}

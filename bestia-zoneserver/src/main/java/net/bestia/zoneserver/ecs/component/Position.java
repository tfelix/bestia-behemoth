package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

public class Position extends Component {
	
	public int x;
	public int y;
	
	public Position(int x, int y) {
		this.x = x;
		this.y = y;
	}

}

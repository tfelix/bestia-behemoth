package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

import net.bestia.zoneserver.zone.shape.Vector2;

public class Position extends Component {
	
	public Vector2 position;
	
	public Position() {
		
	}
	
	public Position(int x, int y) {
		setPosition(x, y);
	}
	
	public void setPosition(int x, int y) {
		position = new Vector2(x, y);
	}
}

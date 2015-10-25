package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

import net.bestia.zoneserver.zone.shape.CollisionShape;

public class Position extends Component {
	
	public CollisionShape position;
	
	public Position() {
		
	}
	
	public Position(int x, int y) {
		setPosition(x, y);
	}
	
	public void setPosition(int x, int y) {
		position = position.moveByAnchor(x, y);
	}
}

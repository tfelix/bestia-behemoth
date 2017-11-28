package net.bestia.entity.component;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

import net.bestia.model.geometry.Point;

/**
 * If this component is added to an entity it will start moving along the path
 * saved into this component. If the path is completely resolved the component is
 * removed.
 * 
 * @author Thomas Felix
 *
 */
@ComponentSync(SyncType.ALL)
@ComponentActor("net.bestia.zoneserver.actor.entity.component.MovementComponentActor")
public class MoveComponent extends Component {

	private static final long serialVersionUID = 1L;
	
	private final Queue<Point> path = new LinkedList<>();

	public MoveComponent(long id) {
		super(id);
		// no op.
	}

	public Queue<Point> getPath() {
		return path;
	}

	@Override
	public int hashCode() {
		return Objects.hash(path);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof MoveComponent)) {
			return false;
		}
		MoveComponent other = (MoveComponent) obj;
		if (path == null) {
			if (other.path != null) {
				return false;
			}
		} else if (!path.equals(other.path)) {
			return false;
		}
		return true;
	}
}

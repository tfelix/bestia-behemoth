package net.bestia.entity.component;

import net.bestia.model.geometry.Point;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

/**
 * If this component is added to an entity it will start moving along the path
 * saved into this component. If the path is completely resolved the component
 * is removed.
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

	public void setPath(Collection<Point> path) {
		this.path.clear();
		this.path.addAll(path);
	}

	@Override
	public String toString() {
		return String.format("MoveComponent[id: %d, eid: %d, path: %s]", getId(),
				getEntityId(),
				path);
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
		return path.equals(other.path);
	}
}

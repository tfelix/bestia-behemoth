package net.bestia.entity.component;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * These attacks are checked if the bestia wants to perform an attack. It also
 * uses this information when calculating the AI reactions of the bestia.
 * 
 * @author Thomas Felix
 *
 */
public class AttackListComponent extends Component {

	private static final long serialVersionUID = 1L;

	private final Set<Integer> knownAttacks = new HashSet<>();

	public AttackListComponent(long id) {
		super(id);
		// no op.
	}

	/**
	 * Checks if the bestia knows the given attack it.
	 * 
	 * @param attackId
	 *            The attack id to check.
	 * @return TRUE if the bestia knows it. FALSE otherwise.
	 */
	public boolean knowsAttack(int attackId) {
		return knownAttacks.contains(attackId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(knownAttacks);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}

		final AttackListComponent other = (AttackListComponent) obj;
		return Objects.equals(this.knownAttacks, other.knownAttacks);
	}

	
}

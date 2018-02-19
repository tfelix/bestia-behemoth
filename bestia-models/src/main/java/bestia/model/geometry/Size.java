package bestia.model.geometry;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Embeddable;

/**
 * Immutable size object.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Embeddable
public final class Size implements Serializable {

	private static final long serialVersionUID = 1L;
	private final long width;
	private final long height;

	/**
	 * Zero argument ctor for Hibernate.
	 */
	public Size() {
		this(0, 0);
	}

	public Size(long width, long height) {
		if (width < 0 || height < 0) {
			throw new IllegalArgumentException("Width and Height must be 0 or bigger.");
		}

		this.width = width;
		this.height = height;
	}

	public long getWidth() {
		return width;
	}

	public long getHeight() {
		return height;
	}

	@Override
	public int hashCode() {
		return Objects.hash(width, height);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Size other = (Size) obj;
		if (height != other.height)
			return false;
		if (width != other.width)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format("Size[width: %d, height: %d]", width, height);
	}
}

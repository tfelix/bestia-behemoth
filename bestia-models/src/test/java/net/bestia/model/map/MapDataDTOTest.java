package net.bestia.model.map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import net.bestia.model.geometry.Rect;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MapDataDTOTest {

	private static final List<Integer> IDS1 = Arrays.asList(10, 11);
	private static final List<Integer> IDS2 = Arrays.asList(12);


	@Test(expected = IllegalArgumentException.class)
	public void join_nonAdjacentDTO_throws() {

		final Rect r1 = new Rect(10, 10, 10, 10);
		final Rect r2 = new Rect(23, 10, 10, 10);

		MapDataDTO md1 = new MapDataDTO(r1);
		MapDataDTO md2 = new MapDataDTO(r2);

		md1.join(md2);
	}

	@Test
	public void getGroundGid_borderCoordinates_ok() {

		final Rect r1 = new Rect(10, 20, 10, 10);
		MapDataDTO md1 = new MapDataDTO(r1);
		fillGid(md1, 1);

		assertEquals(1, md1.getGroundGid(10, 20));
		assertEquals(1, md1.getGroundGid(20, 20));
		assertEquals(1, md1.getGroundGid(10, 30));
		assertEquals(1, md1.getGroundGid(20, 30));
		assertEquals(1, md1.getGroundGid(15, 25));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void getGroundGid_outOfBoundsCords_throws() {

		final Rect r1 = new Rect(10, 20, 10, 10);
		MapDataDTO md1 = new MapDataDTO(r1);
		fillGid(md1, 1);

		assertEquals(1, md1.getGroundGid(21, 23));
	}

	@Test
	public void putGroundLayer_borderCoordinates_ok() {

		final Rect r1 = new Rect(10, 20, 10, 10);
		MapDataDTO md1 = new MapDataDTO(r1);

		md1.putGroundLayer(10, 20, 1);
		md1.putGroundLayer(20, 20, 2);
		md1.putGroundLayer(10, 30, 3);
		md1.putGroundLayer(20, 30, 4);
		md1.putGroundLayer(15, 25, 5);

		assertEquals(1, md1.getGroundGid(10, 20));
		assertEquals(2, md1.getGroundGid(20, 20));
		assertEquals(3, md1.getGroundGid(10, 30));
		assertEquals(4, md1.getGroundGid(20, 30));
		assertEquals(5, md1.getGroundGid(15, 25));
	}
	
	@Test(expected=IndexOutOfBoundsException.class)
	public void putGroundLayer_notInDtoRect_throws() {

		final Rect r1 = new Rect(10, 20, 10, 10);
		MapDataDTO md1 = new MapDataDTO(r1);

		md1.putLayer(0, 5, 20, 1337);
	}

	@Test
	public void getRect_correctRect() {
		final Rect r1 = new Rect(10, 20, 10, 10);
		MapDataDTO md1 = new MapDataDTO(r1);
		assertEquals(r1, md1.getRect());
	}

	@Test
	public void slice_containingRect_ok() {
		final Rect r1 = new Rect(10, 20, 10, 10);
		MapDataDTO md1 = new MapDataDTO(r1);
		md1.putGroundLayer(10, 20, 1);
		md1.putGroundLayer(12, 20, 2);
		md1.putGroundLayer(20, 21, 2);
		md1.putGroundLayer(19, 21, 1);
		md1.putLayer(1, 10, 20, 4);
		md1.putLayer(1, 14, 23, 5);
		md1.putLayer(1, 16, 23, 6);
		
		Rect sliceRect = new Rect(10, 20, 5, 5);
		MapDataDTO sliced = md1.slice(sliceRect);
		Set<Integer> GIDS = new HashSet<>(Arrays.asList(1, 2, 4, 5));
		
		assertEquals(sliceRect, sliced.getRect());
		assertEquals(GIDS, sliced.getDistinctGids());
		assertThat(sliced.getDistinctGids(), not(contains(6)));
		assertEquals(1, sliced.getGroundGid(10, 20));
		
		assertThat(sliced.getLayerGids(10, 20), contains(4));
		assertThat(sliced.getLayerGids(10, 20), not(contains(6)));
	}

	@Test(expected = IllegalArgumentException.class)
	public void slice_outOfBoundsRect_throws() {
		final Rect r1 = new Rect(10, 20, 10, 10);
		MapDataDTO md1 = new MapDataDTO(r1);
		Rect sliceRect = new Rect(18, 20, 8, 8);
		md1.slice(sliceRect);
	}

	@Test
	public void getDistinctGids_distinctGids() {
		final Rect r1 = new Rect(10, 20, 10, 10);
		MapDataDTO md1 = new MapDataDTO(r1);

		md1.putGroundLayer(10, 20, 1);
		md1.putGroundLayer(20, 20, 2);
		md1.putGroundLayer(10, 30, 2);
		md1.putGroundLayer(20, 30, 1);
		md1.putGroundLayer(15, 25, 5);
		md1.putLayer(1, 14, 23, 4);
		md1.putLayer(2, 14, 23, 6);
		
		Set<Integer> GIDS = new HashSet<>(Arrays.asList(1, 2, 4, 5, 6));
		assertEquals(GIDS, md1.getDistinctGids());
	}
	

	@Test
	public void join_rightLeftAdjacentDTO_ok() {

		final Rect r1 = new Rect(20, 20, 10, 10);
		final Rect r2 = new Rect(31, 20, 10, 10);

		MapDataDTO md1 = new MapDataDTO(r1);
		MapDataDTO md2 = new MapDataDTO(r2);

		fillGid(md1, 1);
		fillGid(md2, 2);

		MapDataDTO joined1 = md1.join(md2);
		MapDataDTO joined2 = md2.join(md1);

		final Rect joinedRect = new Rect(20, 20, 20, 10);

		Assert.assertEquals(joinedRect, joined1.getRect());
		Assert.assertEquals(joinedRect, joined2.getRect());
		Assert.assertEquals(joined2.getRect(), joined2.getRect());

		assertEquals(IDS1, joined1.getLayerGids(22, 23));
		assertEquals(IDS1, joined2.getLayerGids(22, 23));
		assertEquals(IDS2, joined1.getLayerGids(25, 5));
		assertEquals(IDS2, joined2.getLayerGids(25, 5));

		// Check gids
		assertEquals(1, joined1.getGroundGid(20, 31));
		assertEquals(1, joined2.getGroundGid(20, 31));
		assertEquals(2, joined1.getGroundGid(20, 10));
		assertEquals(2, joined2.getGroundGid(20, 10));
	}

	@Test
	public void join_topBottomtAdjacentDTO_ok() {

		final Rect r1 = new Rect(20, 21, 10, 10);
		final Rect r2 = new Rect(20, 10, 10, 10);

		MapDataDTO md1 = new MapDataDTO(r1);
		MapDataDTO md2 = new MapDataDTO(r2);

		// Fill GIDs
		fillGid(md1, 1);
		fillGid(md2, 2);

		// Fill sparse layers
		md1.putLayer(1, 22, 23, 10);
		md1.putLayer(2, 22, 23, 11);
		md2.putLayer(1, 25, 5, 12);

		MapDataDTO joined1 = md1.join(md2);
		MapDataDTO joined2 = md2.join(md1);

		final Rect joinedRect = new Rect(20, 10, 10, 20);

		Assert.assertEquals(joinedRect, joined1.getRect());
		Assert.assertEquals(joinedRect, joined2.getRect());
		Assert.assertEquals(joined2.getRect(), joined2.getRect());

		assertEquals(IDS1, joined1.getLayerGids(22, 23));
		assertEquals(IDS1, joined2.getLayerGids(22, 23));
		assertEquals(IDS2, joined1.getLayerGids(25, 5));
		assertEquals(IDS2, joined2.getLayerGids(25, 5));

		// Check gids
		assertEquals(1, joined1.getGroundGid(20, 31));
		assertEquals(1, joined2.getGroundGid(20, 31));
		assertEquals(2, joined1.getGroundGid(20, 10));
		assertEquals(2, joined2.getGroundGid(20, 10));
	}

	private void fillGid(MapDataDTO dto, int gid) {

		final long maxY = dto.getRect().getY() + dto.getRect().getHeight();
		final long maxX = dto.getRect().getX() + dto.getRect().getWidth();

		for (long y = dto.getRect().getY(); y < maxY; ++y) {
			for (long x = dto.getRect().getX(); x < maxX; ++x) {
				dto.putGroundLayer(x, y, gid);
			}
		}
	}

}

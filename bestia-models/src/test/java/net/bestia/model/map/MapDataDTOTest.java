package net.bestia.model.map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.model.geometry.Rect;

@RunWith(MockitoJUnitRunner.class)
public class MapDataDTOTest {
	
	@Before
	public void setup() {
		
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void join_nonAdjacentDTO_throws() {
		
		final Rect r1 = new Rect(10, 10, 10, 10);
		final Rect r2 = new Rect(23, 10, 10, 10);
		
		MapDataDTO md1 = new MapDataDTO(r1);
		MapDataDTO md2 = new MapDataDTO(r2);
		
		md1.join(md2);
	}
	
	@Test
	public void join_rightLeftAdjacentDTO_ok() {
		
		final Rect r1 = new Rect(20, 20, 10, 10);
		final Rect r2 = new Rect(31, 20, 10, 10);
		
		MapDataDTO md1 = new MapDataDTO(r1);
		MapDataDTO md2 = new MapDataDTO(r2);
		
		fillGid(md1, 1);
		fillGid(md2, 2);
		
		MapDataDTO joined1 =  md1.join(md2);
		MapDataDTO joined2 =  md2.join(md1);
		
		final Rect joinedRect = new Rect(20, 20, 20, 10);
		
		Assert.assertEquals(joinedRect, joined1.getRect());
		Assert.assertEquals(joinedRect, joined2.getRect());
		Assert.assertEquals(joined2.getRect(), joined2.getRect());
		
		// TODO Check the gids.
	}
	
	@Test
	public void join_topBottomtAdjacentDTO_ok() {
		
		final Rect r1 = new Rect(20, 21, 10, 10);
		final Rect r2 = new Rect(20, 10, 10, 10);
		
		MapDataDTO md1 = new MapDataDTO(r1);
		MapDataDTO md2 = new MapDataDTO(r2);
		
		fillGid(md1, 1);
		fillGid(md2, 2);
		
		MapDataDTO joined1 =  md1.join(md2);
		MapDataDTO joined2 =  md2.join(md1);
		
		final Rect joinedRect = new Rect(20, 10, 10, 20);
		
		Assert.assertEquals(joinedRect, joined1.getRect());
		Assert.assertEquals(joinedRect, joined2.getRect());
		Assert.assertEquals(joined2.getRect(), joined2.getRect());
		
		// TODO Check the gids.
	}
	
	private void fillGid(MapDataDTO dto, int gid) {
		
		final long maxY = dto.getRect().getY() + dto.getRect().getHeight();
		final long maxX = dto.getRect().getX() + dto.getRect().getWidth();
		
		for(long y = dto.getRect().getY(); y < maxY; ++y) {
			for(long x = dto.getRect().getX(); x < maxX; ++x) {
				dto.putGroundLayer(x, y, gid);
			}
		}
	}

}

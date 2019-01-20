package net.bestia.zoneserver.map;

import net.bestia.model.map.MapDataRepository;
import net.bestia.model.map.MapParameterRepository;
import net.bestia.model.map.MapParameter;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.BestiaMap;
import net.bestia.model.map.MapChunk;
import net.bestia.model.map.TilesetService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MapServiceTest {

	private static final String MAP_NAME = "kalarian";

	private MapService ms;

	@Mock
	private MapDataRepository dataNoMapDao;

	@Mock
	private MapDataRepository dataMapDao;

	@Mock
	private MapParameterRepository paramDao;

	@Mock
	private MapParameterRepository paramNoMapDao;

	@Mock
	private MapParameter mapParams;

	@Mock
	private TilesetService tilesetService;

	@Before
	public void setup() {

		when(dataMapDao.count()).thenReturn(1L);
		when(mapParams.getName()).thenReturn(MAP_NAME);
		when(paramDao.findFirstByOrderByIdDesc()).thenReturn(mapParams);

		ms = new MapService(dataNoMapDao, paramDao, tilesetService);
	}

	@Test
	public void isMapInitialized_noMapInsideDB_false() {
		Assert.assertFalse(ms.isMapInitialized());
	}

	@Test
	public void isMapInitialized_mapInsideDB_true() {
		ms = new MapService(dataMapDao, paramDao, tilesetService);
		Assert.assertTrue(ms.isMapInitialized());
	}

	@Test(expected = IllegalArgumentException.class)
	public void getMap_illegalCoordinates_throws() {
		ms.getMap(10, 10, -10, 10);
	}

	@Test
	public void getMap_legalCoordinates_validMap() {

		BestiaMap m = ms.getMap(5, 10, 10, 10);

		Assert.assertNotNull(m);
		Assert.assertEquals(new Rect(5, 10, 10, 10), m.getRect());
	}

	@Test(expected = NullPointerException.class)
	public void saveMapData_null_throws() throws IOException {
		ms.saveMapData(null);
	}

	@Test
	public void getMapName_noMapInsideDB_emptyStr() {
		ms = new MapService(dataNoMapDao, paramNoMapDao, tilesetService);
		Assert.assertEquals("", ms.getMapName());
	}

	@Test
	public void getMapName_mapInsideDB_validStr() {
		Assert.assertEquals(MAP_NAME, ms.getMapName());
	}

	@Test(expected = NullPointerException.class)
	public void getChunks_null_throws() {
		ms.getChunks(null);
	}

	@Test
	public void getChunks_validCords_listWithChunks() {
		List<Point> chunkCords = new ArrayList<>();
		chunkCords.add(new Point(1, 1));
		List<MapChunk> chunks = ms.getChunks(chunkCords);

		Assert.assertNotNull(chunks);
		Assert.assertEquals(1, chunks.size());
	}
}

package net.bestia.zoneserver.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

import net.bestia.model.dao.MapDataDAO;
import net.bestia.model.dao.MapParameterDAO;
import net.bestia.model.domain.MapParameter;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.model.map.MapChunk;
import net.bestia.zoneserver.map.MapService;

public class MapServiceTest {

	private static final String MAP_NAME = "kalarian";

	private MapService ms;

	private MapDataDAO dataNoMapDao;
	private MapDataDAO dataMapDao;
	private MapParameterDAO paramDao;
	private MapParameterDAO paramNoMapDao;
	private MapParameter mapParams;

	@Before
	public void setup() {
		
		dataNoMapDao = mock(MapDataDAO.class);
		
		dataMapDao = mock(MapDataDAO.class);
		when(dataMapDao.count()).thenReturn(1L);
		
		mapParams = mock(MapParameter.class);
		when(mapParams.getName()).thenReturn(MAP_NAME);
		
		paramNoMapDao = mock(MapParameterDAO.class);
		paramDao = mock(MapParameterDAO.class);
		when(paramDao.findLatest()).thenReturn(mapParams);
		
		ms = new MapService(dataNoMapDao, paramDao);
	}

	@Test
	public void isMapInitialized_noMapInsideDB_false() {
		Assert.assertFalse(ms.isMapInitialized());
	}

	@Test
	public void isMapInitialized_mapInsideDB_true() {
		ms = new MapService(dataMapDao, paramDao);
		Assert.assertTrue(ms.isMapInitialized());
	}

	@Test(expected = IllegalArgumentException.class)
	public void getMap_illegalCoordinates_throws() {
		ms.getMap(10, 10, -10, 10);
	}

	@Test
	public void getMap_legalCoordinates_validMap() {
		
		Map m = ms.getMap(0, 0, 0, 0);
		
		Assert.assertNotNull(m);

		m = ms.getMap(5, 10, 10, 10);

		Assert.assertEquals(new Rect(5, 10, 10, 10), m.getRect());
	}

	@Test(expected = NullPointerException.class)
	public void saveMapData_null_throws() throws IOException {
		ms.saveMapData(null);
	}

	@Test
	public void getMapName_noMapInsideDB_emptyStr() {
		ms = new MapService(dataNoMapDao, paramNoMapDao);
		Assert.assertEquals("", ms.getMapName());
	}

	@Test
	public void getMapName_mapInsideDB_validStr() {
		Assert.assertEquals(MAP_NAME, ms.getMapName());
	}

	@Test(expected = NullPointerException.class)
	public void getAreaName_null_throws() {
		ms.getAreaName(null);
	}

	/*
	@Test
	public void getAreaName_validPoint_nameOfArea() {
		Assert.assertEquals(AREA_NAME, ms.getAreaName(new Point(1, 42)));
	}*/

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

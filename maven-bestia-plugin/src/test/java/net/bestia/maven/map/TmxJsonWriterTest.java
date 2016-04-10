package net.bestia.maven.map;

import java.io.File;

import org.junit.Test;

import tiled.core.Map;
import tiled.io.TMXMapReader;

public class TmxJsonWriterTest {

	@Test
	public void write_multilayer_ok() throws Exception {
		final File tmxMapFile = new File(this.getClass().getResource("/layer_test/layer_test.tmx").getFile());

		final TMXMapReader reader = new TMXMapReader(true);
		final Map map = reader.readMap(tmxMapFile.getAbsolutePath());
		
		
		
		final TmxJsonWriter writer = new TmxJsonWriter();
		
		writer.write(map, new File("C:\\Users\\Thomas\\test.json"));
		
	}
	
}

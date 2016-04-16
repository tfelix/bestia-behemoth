package net.bestia.util;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

public class VersionReaderTest {

	@Test
	public void getVersion_stdFile_ok() {
		VersionReader vr = new VersionReader();
		Assert.assertEquals("alpha-0.2.7", vr.getVersion());
	}

	@Test
	public void getName_stdFile_ok() {
		VersionReader vr = new VersionReader();
		Assert.assertEquals("bestia", vr.getName());
	}

	@Test
	public void getVersion_extraFile_ok() {
		VersionReader vr = new VersionReader(getFile());
		Assert.assertEquals("1.0.0", vr.getVersion());
	}

	@Test
	public void getName_extraFile_ok() {
		VersionReader vr = new VersionReader(getFile());
		Assert.assertEquals("bestia2", vr.getName());
	}
	
	private File getFile() {
		final URL url = ClassLoader.getSystemResource("version2.properties");
		try {
			return new File(url.toURI());
		} catch (URISyntaxException e) {
			return null;
		}
	}

}

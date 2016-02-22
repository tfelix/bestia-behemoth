package net.bestia.maven.util;


import java.io.File;

import org.junit.Assert;
import org.junit.Test;

public class FilePathHelperTest {
	
	private static File rootDir = new File("\\assets");
	
	@Test(expected=IllegalArgumentException.class)
	public void ctor_nullBaseDir_exception() {
		new FilePathHelper(null);
	}
	
	@Test
	public void getMapScript_ok_file() {
		final FilePathHelper fph = new FilePathHelper(rootDir);
		final File f = fph.getMapScript("test", "hello");
		final String path = f.getPath();
		Assert.assertTrue(path.equals("\\assets\\script\\map\\test\\hello.groovy"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void getMapScript_nullMapName_exception() {
		final FilePathHelper fph = new FilePathHelper(rootDir);
		fph.getMapScript(null, "hello");
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void getMapScript_nullScriptName_exception() {
		final FilePathHelper fph = new FilePathHelper(rootDir);
		fph.getMapScript("test", null);
	}

	@Test
	public void getItemScript_ok_file() {
		final FilePathHelper fph = new FilePathHelper(rootDir);
		final File f = fph.getItemScript("apple");
		final String path = f.getPath();
		Assert.assertTrue(path.equals("\\assets\\script\\item\\apple.groovy"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void getItemScript_nullName_exception() {
		final FilePathHelper fph = new FilePathHelper(rootDir);
		fph.getItemScript(null);
	}
	
	@Test
	public void getAttackScript_ok_file() {
		final FilePathHelper fph = new FilePathHelper(rootDir);
		final File f = fph.getAttackScript("tackle");
		final String path = f.getPath();
		Assert.assertTrue(path.equals("\\assets\\script\\attack\\tackle.groovy"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void getAttackScript_nullName_exception() {
		final FilePathHelper fph = new FilePathHelper(rootDir);
		fph.getAttackScript(null);
	}
	
	@Test
	public void getMapSound_ok_file() {
		final FilePathHelper fph = new FilePathHelper(rootDir);
		final File f = fph.getMapSound("hello.mp3");
		final String path = f.getPath();
		Assert.assertTrue(path.equals("\\assets\\sound\\bgm\\hello.mp3"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void getMapSound_nullName_exception() {
		final FilePathHelper fph = new FilePathHelper(rootDir);
		fph.getMapSound(null);
	}
}

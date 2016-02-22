package net.bestia.maven.map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * This mojo will check if all referenced files are accessible. Often there are
 * packs or script files which in turn reference different files from the asset
 * pack. These references are checked for existence. The files will be also
 * checked if they can be correctly parsed. However there will be no check if
 * there is a semantic error.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Mojo(name = "validate-references", defaultPhase = LifecyclePhase.VALIDATE)
public class ValidateReferences extends AbstractMojo {

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		
		// TODO Check the mob packs.
		
		// TODO Check the attack packs.
		
	}

}

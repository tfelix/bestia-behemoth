package net.bestia.maven.api;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * This mojo will check if all sprites are correctly setup. This especially
 * means if the JSON description files match the standards and if every
 * information is included (animation etc.)
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Mojo(name = "validate-assets", defaultPhase = LifecyclePhase.VALIDATE)
public class ValidateSprites extends AbstractMojo {

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		// TODO Check the Sprite folders.

	}

}

package bestia.messages.ui;

/**
 * Different types of dialogs. Will be displayed differently on the client.
 * 
 * @author Thomas
 *
 */
public enum DialogAction {

	/**
	 * The name node will change the name of the displayed NPC to the player.
	 */
	NAME,

	/**
	 * This text will be simply shown to the user.
	 */
	TEXT,

	/**
	 * This is an image which will be shown inline (!) in the dialog box.
	 */
	IMAGE,

	/**
	 * This image will be shown at the bottom as a portrait image at the bottom
	 * of the screen.
	 */
	IMAGE_PORTRAIT_BOTTOM_LEFT,
	
	/**
	 * Closes the dialog.
	 */
	CLOSE
	
}

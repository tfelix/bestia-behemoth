/* global Bestia:true */
/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

/**
 * @namespace Bestia
 */
var Bestia = Bestia || {
	/**
	 * Bestia client version number.
	 * 
	 * @constant
	 * @type {string}
	 */
	VERSION: 'alpha-0.2.5-SNAPSHOT',
	

	/**
	 * Holds various hard coded URLs. These might be different in production and
	 * in local use. They will be replaced by the build system upon release.
	 * 
	 * @constant
	 * @property {string} Urls.loginHtml - URL of the login page.
	 * @property {string} Urls.gameHtml - URL of the main game page.
	 * @property {string} Urls.bestiaWebAPI - URL of the login API endpoint.
	 * @property {string} Urls.bestiaWebsocket - URL of the websocket API
	 *           endpoint.
	 */
	Urls : {
		/* @ifdef DEVELOPMENT **
		loginHtml : 'http://localhost/login.html',
		gameHtml : 'http://localhost/index.html',
		bestiaWebAPI : 'http://localhost:8090',	
		bestiaWebsocket: 'http://localhost:8080/api',	
		assetsRoot : 'http://localhost/assets/',
		assetsMap : 'http://localhost/assets/map/',
		assetsItems : 'http://localhost/assets/img/items/',
		assetsMobIcon : 'http://localhost/assets/img/mob-icon/',
		assetsMobSprite : 'http://localhost/assets/img/sprite/mob/'
		/* @endif */
		/* @ifdef PRODUCTION **
		loginHtml : 'http://www.bestia-game.net/login.html',
		gameHtml : 'http://www.bestia-game.net/index.html',
		bestiaWebAPI : 'http://login.bestia-game.net',
		bestiaWebsocket : 'http://socket.bestia-game.net/api',
		assetsRoot : 'http://www.bestia-game.net/assets/',
		assetsMap : 'http://www.bestia-game.net/assets/map/',
		assetsItems : 'http://www.bestia-game.net/assets/img/items/',
		assetsMobIcon : 'http://www.bestia-game.net/assets/img/mob-icon/',
		assetsMobSprite : 'http://www.bestia-game.net/assets/img/sprite/mob/'
		/* @endif */
	}
};



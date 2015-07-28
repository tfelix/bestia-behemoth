var BG = {};

function bootstrap() {
	// Bootstrap the behemoth.
	BG.config = new Bestia.Config();
	BG.net = new Bestia.Net(BG.config);
	BG.inventory = new Bestia.Inventory(BG.net);
	BG.bestias = new Bestia.BestiaInfoViewModel(BG.net);	
	BG.chat = new Bestia.Chat($('#chat'), BG);
	BG.engine = new Bestia.Engine(BG.config);
	BG.connection = new Bestia.Connection();
	
	BG.connection.init();

	// UI init must wait until dom is loaded and accessible.
	$(document).ready(function() {
		BG.page = {
			logoutDialog : new Bestia.Page.LogoutDialog('#modal-logout')
		};
		
		// Bind the DOM to the game.
		ko.applyBindings(BG);
		
		//$('#modal-inventory').modal('show');
	});

}

// Final code.
i18n.init({
	lng : "de",
	fallbackLng : false
}, function() {
	$('body').i18n();

	// Setup the game object.
	bootstrap();
});
import UrlHelper from '../UrlHelper.js';
import Storage from '../util/Storage.js';

$(document).ready(function() {
	
	// Login Form vorbereiten.
	$('#login').submit(function() {
		var email = $('#inputEmail').val();
		var password = $('#inputPassword').val();

		$.get(Urls.bestiaWebAPI + "/v1/account/login", {
			ident : email,
			password : password
		}, function(data) {

			// Save cookie with this data.
			var storage = new Storage();
			storage.storeAuth(data);

			window.location.href = Urls.gameHtml;
			
		}).fail(function() {
			$('#login').addClass('has-error');
		});

		return false;
	});
});

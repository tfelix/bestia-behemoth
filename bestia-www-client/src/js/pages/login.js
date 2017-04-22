import 'zepto';
import UrlHelper from '../util/UrlHelper.js';
import Storage from '../util/Storage.js';

Zepto(function($){
	// Login Form vorbereiten.
	$('#login').on('submit', function(){
		var email = $('#username').val();
		var password = $('#password').val();

		$.get('http://localhost:8080/v1/account/login', {
			ident : email,
			password : password
		}, function(data) {

			// Save cookie with this data.
			var storage = new Storage();
			storage.storeAuth(data);

			window.location.href = UrlHelper.gameUrl;
			
		}).fail(function() {
			$('#login').addClass('has-error');
		});

		return false;
	});
});


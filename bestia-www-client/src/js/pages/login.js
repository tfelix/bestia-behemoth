$(document).ready(function() {
	// Login Form vorbereiten.
	$('#login').submit(function() {
		var email = $('#inputEmail').val();
		var password = $('#inputPassword').val();

		$.get(Bestia.Urls.bestiaWebAPI + "/v1/account/login", {
			ident : email,
			password : password
		}, function(data) {

			// Save cookie with this data.
			var storage = new Bestia.Storage();
			storage.storeAuth(data);

			window.location.href = Bestia.Urls.gameHtml;
		}).fail(function(jqXHR, textStatus, errorThrown) {
			$('#login').addClass('has-error');
			
			console.debug("Error while login: " + errorThrown + ", Status: " + textStatus);
		});

		return false;
	});
});

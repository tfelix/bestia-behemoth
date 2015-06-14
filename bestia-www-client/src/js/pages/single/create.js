var CreateForm = function() {

	var self = this;

	this.masterName = ko.observable('');
	this.email = ko.observable('');
	this.password = ko.observable('');
	this.passwordConfirm = ko.observable('');

	/**
	 * @property {boolean} passwordConfirm - Checks if the both input passwords
	 *           are equal.
	 */
	this.passwordMath = ko.pureComputed(function() {
		return self.password() === self.passwordConfirm();
	});

	/**
	 * Checks if the form is validated and then make a request to the server.
	 * 
	 * @method CreateForm#submit
	 */
	this.onSubmit = function() {

		$.ajax({
			url : Bestia.Urls.bestiaWebAPI + "/v1/account/create",
			// TODO hier API austauschen wenn webserver PUT unterstützt.
			type : 'GET',
			data : {
				email : this.email(),
				password : this.password(),
				username : this.masterName(),
				master : 1
			},
			success : function(data) {
				window.location.href = Bestia.Urls.gameHtml;
			},
			error : function() {
				// Something went wrong. Display error.
				console.error("Could not create account.");
			}
		});

		return false;
	};

};

var bestiaForm = new CreateForm();

$(document).ready(function() {
	ko.applyBindings(bestiaForm);
});

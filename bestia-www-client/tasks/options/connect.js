module.exports = {

	dev : {
		options : {
			base : 'build',
			port : 80
		}
	},
	test_debug : {
		options : {
			port : 8000,
			keepalive : true,
			open : {
				target : 'http://localhost:8000/_SpecRunner.html',
				appName : 'Firefox'
			}
		}
	},

	// Damit kann man den Unit Test der Übersetzungs Strings simulieren.
	test_test : {
		options : {
			port : 8000,
			keepalive : true,
			open : {
				target : 'http://localhost:8000/_SpecRunner.html',
				appName : 'Firefox'
			},
			middleware : function(connect, options, middlewares) {
				// inject a custom middleware into the array of default
				// middlewares

				var itemTransl = /assets\/i18n\/(.*)\/item\/\d/;

				middlewares.unshift(function(req, res, next) {

					if (req.url.match(itemTransl)) {
						next();
						return;
					}
					// Item translation.
					res.end('Hello, world from port #' + options.port + '!');

				});

				return middlewares;
			}
		}
	}
};
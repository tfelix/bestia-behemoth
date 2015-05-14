module.exports = function(grunt) {

	// require time grunt.
	require('time-grunt')(grunt);
	
	var jsFiles = ['source/js/bestia.js', 'source/js/config.js', 'source/js/util/net.js', 'source/js/util/pubsub.js', 'source/js/io/message.js',
				'source/js/inventory/inventory.js', 'source/js/view/system.pingpong.js', 'source/js/engine/engine.js',
				'source/js/chat.js'];

	// Project configuration.
	grunt.initConfig({
		pkg : grunt.file.readJSON('package.json'),

		copy : {
			dist : {
				files : [ {
					expand : true,
					cwd : 'source',
					src : [ '**', '!js/**', '!css/**' ],
					dest : 'build'
				}, {
					expand : true,
					cwd : '../game-data',
					src : '**',
					dest : 'build/assets'
				} ]
			}
		},

		clean : {
			build : {
				src : [ 'build' ]
			}
		},

		less : {
			development : {
				options : {
					paths : [ 'source/css/less/**/' ]
				},
				files : {
					'build/css/app.css' : 'source/css/less/main.less'
				}
			}
		},

		connect : {
			server : {
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
					middleware : function(connect, options, middlewares) {
						// inject a custom middleware into the array of default
						// middlewares
						middlewares.unshift(function(req, res, next) {
							
							
							if (req.url.match(/assets\/i18n\/(.*)\/item\/\d+/)) {
								// Item translation.
								res.end('Hello, world from port #' + options.port + '!');
							}

							
							return next();
						});

						return middlewares;
					}
				}
			}
		},

		concat : {
			options : {
				sourceMap : true
			},
			dist : {
				// Custom scripts. Order is important!
				src : jsFiles,
				dest : 'build/js/bestia.js'
			}
		},

		cssmin : {
			development : {
				files : {
					'build/css/app.min.css' : [ 'build/css/**/*.css' ]
				}
			}
		},

		watch : {
			stylesheets : {
				files : [ 'source/css/**/*.less', 'source/css/**/*.css' ],
				tasks : [ 'css:dev' ]
			},
			copy : {
				files : [ 'source/**', '!source/**/*.styl', '!source/**/*.coffee', '!source/**/*.jade' ],
				tasks : [ 'default' ]
			}
		},

		uglify : {
			options : {
				banner : '/*! BESTIA BEHEMOTH V.<%= pkg.name %> <%= grunt.template.today("yyyy-mm-dd") %> */\n'
			},
			build : {
				src : 'src/<%= pkg.name %>.js',
				dest : 'build/<%= pkg.name %>.min.js'
			}
		},

		bower_concat : {
			dist : {
				dest : 'build/js/libs.js',
				cssDest : 'build/css/libs.css'
			}
		},

		// ============ DEVELOPMENT =============
		jasmine : {
			all : {
				src : jsFiles,
				options : {
					specs : 'specs/**/*Spec.js',
					vendor : 'build/js/libs.js'
				},
				keepRunner : true
			}
		},

		jshint : {
			src : [ 'source/js/**/*.js', '!source/js/lib/**'],
			options : {
				jshintrc : '.jshintrc'
			}
		}
	});

	// Load the tasks.
	require('load-grunt-tasks')(grunt);

	grunt.registerTask('default', 'Builds the project and packages it for distribution.', [ 'compile' ]);

	grunt.registerTask('dev', 'Testing of the framework.', [ 'test', 'connect:test_test' ]);
	grunt.registerTask('dev-test', 'Testing of the framework.', [ 'test', 'connect', 'watch' ]);

	grunt.registerTask('compile-js', 'Compiles JS files.', 'bower_concat', 'concat');
	grunt.registerTask('compile-css', 'Compiles CSS files.', [ 'less' ]);
	grunt.registerTask('compile-html', 'Compile HTML files.');

	grunt.registerTask('compile', 'Compile all.', [ 'clean', 'copy', 'compile-css', 'compile-js' ]);

	grunt.registerTask('test', 'Testing of the framework.', [ 'jshint', 'jasmine:all:build' ]);

	grunt.registerTask('dist', 'Packages the build files for distribution.', function() {
	});
};
module.exports = function(grunt) {
	
	// require time grunt.
    require('time-grunt')(grunt);

	// Project configuration.
	grunt.initConfig({
		pkg : grunt.file.readJSON('package.json'),

		copy : {
			dist : {
				files : [ 
				{expand : true, cwd : 'source', src : ['**', '!js/**', '!css/**'],dest : 'build'}, 
				{expand : true, cwd : '../game-data', src : '**', dest : 'build/assets'}
				]
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
					paths : [ 'build/css/less/**/' ]
				},
				files : {
					'build/css/app.css' : 'build/css/less/main.less'
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
					port: 8000,
					keepalive: true,
					open: {
						target: 'http://localhost:8000/_SpecRunner.html',
						appName: 'Firefox'
					}
				}
			},
			
			// Damit kann man den Unit Test der Übersetzungs Strings simulieren.
			test_test: {
				options: {
					port: 8000,
					keepalive: true,
			        middleware: function(connect, options, middlewares) {
			        	// inject a custom middleware into the array of default
						// middlewares
			        	middlewares.unshift(function(req, res, next) {
			            if (req.url !== '/hello/world') return next();

			            res.end('Hello, world from port #' + options.port + '!');
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
				src : [ 'source/js/config.js', 'source/js/io/message.js', 'source/js/io/preloader.js',
						'source/js/view/bestias.js', 'source/js/view/system.pingpong.js', 'source/js/engine/engine.js',
						'source/js/chat.js',

						'source/js/main.js' ],
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
				banner : '/*! <%= pkg.name %> <%= grunt.template.today("yyyy-mm-dd") %> */\n'
			},
			build : {
				src : 'src/<%= pkg.name %>.js',
				dest : 'build/<%= pkg.name %>.min.js'
			}
		},
		
		bower_concat : {
			dist: {
				dest: 'build/js/libs.js',
				cssDest: 'build/css/libs.css',
				mainFiles: {
					'jquery-tiny-pubsub': ['dist/ba-tiny-pubsub.js']
				}
			}
		},
		
		// ============ DEVELOPMENT =============
		jasmine: {
			all: {// src: 'source/**/*.js', Temporär ersetzt durch eine Date
					// um das neue Format zu erproben.
				src: ['source/js/bestia.js', 'source/js/config.js', 'source/js/util/net.js', 'source/js/view/inventory.js'],
				options: {
					specs: 'specs/**/*Spec.js',
					vendor: 'build/js/libs.js'
				},
				keepRunner: true
			}
		},
		  
		 jshint: {
			 src: ['source/js/**/*.js', '!source/js/lib/**'],
			 options: { jshintrc: '.jshintrc' }
		}
	});

	// Load the tasks.
	require('load-grunt-tasks')(grunt);


	grunt.registerTask('default', 'Watches the project for changes automatically builds them.', ['compile', 'connect', 'watch' ]);
	
	grunt.registerTask('compile', 'Creates a complete build of the system.', ['clean', 'copy', 'bower_concat', 'less', 'concat']);
	
	// grunt.registerTask('optimize', []); Minimiert und optimiert alle Scripte
	// und Ressourcen.
	
	// This is not finished yet. We have to perform a build first.
	grunt.registerTask('test', ['bower_concat', 'jshint', 'jasmine']);
	grunt.registerTask('release', 'Builds the release version and optimizes it.' ['clean', 'test']);
	/**
	 * Prepares a spec runner file, starts a webserver and displays the unit
	 * test runs.
	 */
	grunt.registerTask('debug', ['jshint', 'jasmine:all:build', 'connect:test_debug']);
};
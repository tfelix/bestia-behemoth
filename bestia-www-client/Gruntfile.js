module.exports = function(grunt) {

	// require time grunt.
    require('time-grunt')(grunt);

	// Project configuration.
	grunt.initConfig({
		pkg : grunt.file.readJSON('package.json'),

		copy : {
			build : {
				files : [ {
					expand : true,
					cwd : 'source',
					src : '**',
					dest : 'build'
				}, {
					expand: true,
					cwd: '../game-data',
					src : '**',
					dest : 'build/assets'
				} ]
			}
		},

		clean : {
			build : {
				src : [ 'build' ]
			},
			stylesheets : {
				src : [ 'build/**/*.css', '!build/application.css' ]
			},
			scripts : {
				src : [ 'build/**/*.js', '!build/application.js' ]
			},
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
			        	// inject a custom middleware into the array of default middlewares
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
				src : [ 'build/js/vendor/jquery/jquery-2.1.3.js', 'build/js/vendor/jquery/jquery.atmosphere.js',
						'build/js/vendor/jquery/ba-tiny-pubsub.js',
						'build/js/vendor/createjs/createjs-2014.12.12.min.js', 'build/js/vendor/proton-1.0.0.js',
						'build/js/vendor/knockout-3.3.0.js', 'build/js/vendor/i18next-1.7.7.js',

						// Custom scripts. Order is important!
						'build/js/bestia.js', 'build/js/config.js', 'build/js/view/inventory.js'],
				dest : 'build/js/app.js'
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
				tasks : [ 'default' ]
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
		
		
		// ============ DEVELOPMENT =============
		jasmine: {
			all: {//src: 'source/**/*.js', Temporär ersetzt durch eine Date um das neue Format zu erproben.
				src: ['source/js/bestia.js', 'source/js/config.js', 'source/js/util/net.js', 'source/js/view/inventory.js'],
				options: {
					specs: 'specs/**/*Spec.js',
					vendor: ['source/js/vendor/knockout-3.3.0.js', 
					         'source/js/vendor/jquery/jquery-2.1.3.js', 
					         'source/js/vendor/jquery/ba-tiny-pubsub.js']
				},
				keepRunner: true
			}
		},
		  
		 jshint: {
			 src: ['source/js/bestia.js', 'source/js/config.js', 'source/js/view/inventory.js']
			 //src: ['source/js/**/*.js', '!source/js/vendor/**/*.js'],
		}
	});

	// Load the tasks.
	grunt.loadNpmTasks('grunt-contrib-uglify');
	grunt.loadNpmTasks('grunt-contrib-copy');
	grunt.loadNpmTasks('grunt-contrib-clean');
	grunt.loadNpmTasks('grunt-contrib-less');
	grunt.loadNpmTasks('grunt-contrib-watch');
	grunt.loadNpmTasks('grunt-contrib-concat');
	grunt.loadNpmTasks('grunt-contrib-connect');
	grunt.loadNpmTasks('grunt-contrib-jasmine');
	grunt.loadNpmTasks('grunt-contrib-imagemin');
	grunt.loadNpmTasks('grunt-contrib-jshint');
	grunt.loadNpmTasks('grunt-contrib-connect');

	grunt.registerTask('css', 'Compiles  and prepares the stylesheets.', [ 'less' ]);

	grunt.registerTask('default', 'Watches the project for changes automatically builds them.', ['build', 'connect', 'watch' ]);
	
	grunt.registerTask('build', 'Creates a complete build of the system.', ['clean', 'copy', 'css', 'concat']);
	// grunt.registerTask('optimize', []); Minimiert und optimiert alle Scripte und Ressourcen.
	
	
	// This is not finished yet. We have to perform a build first.
	grunt.registerTask('test', ['jshint', 'jasmine']);
	grunt.registerTask('release', 'Builds the release version and optimizes it.' ['clean', 'test']);
	
	/**
	 * Prepares a spec runner file, starts a webserver and displays the unit test runs.
	 */
	grunt.registerTask('debug', ['jshint', 'jasmine:all:build', 'connect:test_debug']);
	

};
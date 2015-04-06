module.exports = function(grunt) {

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
					dest : 'build/resources'
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
						'build/js/io/message.js', 'build/js/io/preloader.js', 'build/js/view/server.info.js',
						'build/js/view/bestias.js', 'build/js/view/system.pingpong.js', 'build/js/engine/engine.js',
						'build/js/chat.js',

						'build/js/main.js' ],
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

	grunt.registerTask('css', 'Compiles  and prepares the stylesheets.', [ 'less' ]);

	grunt.registerTask('default', 'Watches the project for changes automatically builds them.', [ 'clean', 'copy',
			'css', 'concat', 'connect', 'watch' ]);

};
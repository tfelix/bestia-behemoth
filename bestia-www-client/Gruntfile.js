module.exports = function(grunt) {

	// Project configuration.
	grunt.initConfig({
		pkg: grunt.file.readJSON('package.json'),
		
		copy: {
			build: {
				cwd: 'source',
				src: [ '**' ],
				dest: 'build',
				expand: true
			},
		},
		
		clean: {
			build: {
				src: [ 'build' ]
			},
			stylesheets: {
				src: [ 'build/**/*.css', '!build/application.css' ]
			},
			scripts: {
				src: [ 'build/**/*.js', '!build/application.js' ]
			},
		},
		
		less : {
			development: {
				options: {
					paths: [ 'build/css/less/**/' ]
				},
				files: {
					'build/css/app.css' : 'build/css/less/main.less'
				}
			},
			/*production: {}*/
		},
		
		concat: {
			development: {
		      src: ['build/js/vendor/preloadjs/src/preload.js', 'build/js/loader.js', 'build/js/main.js'],
		      dest: 'build/js/app.js'
		    }
		},
		
		cssmin: {
			development: {
				files: {
					'build/css/app.min.css': [ 'build/css/**/*.css' ]
				}
			}
		},
		
		cssbeautifier : {
			files : ['build/css/app.css']
		},
		
		watch: {
			stylesheets: {
				files: ['source/css/**/*.less', 'source/css/**/*.css'],
			tasks: [ 'css:dev' ]
			},
			scripts: {
				files: 'source/**/*.coffee',
				tasks: [ 'scripts' ]
			},
			copy: {
				files: [ 'source/**', '!source/**/*.styl', '!source/**/*.coffee', '!source/**/*.jade' ],
				tasks: [ 'copy' ]
			}
		},

		uglify: {
		  options: {
			banner: '/*! <%= pkg.name %> <%= grunt.template.today("yyyy-mm-dd") %> */\n'
		  },
		  build: {
			src: 'src/<%= pkg.name %>.js',
			dest: 'build/<%= pkg.name %>.min.js'
		  }
		}
	});

	// Load the tasks.
	grunt.loadNpmTasks('grunt-contrib-uglify');
	grunt.loadNpmTasks('grunt-contrib-copy');
	grunt.loadNpmTasks('grunt-contrib-concat');
	grunt.loadNpmTasks('grunt-contrib-clean');
	grunt.loadNpmTasks('grunt-contrib-less');
	grunt.loadNpmTasks('grunt-cssbeautifier');
	grunt.loadNpmTasks('grunt-contrib-watch');
	grunt.loadNpmTasks('grunt-bower-concat');
	grunt.loadNpmTasks('grunt-contrib-concat');
	
	grunt.registerTask(
		'css:dev', 
		'DEVELOPMENT: Compiles the stylesheets.', 
		[ 'less:development', 'cssbeautifier' ]
	);
	
	grunt.registerTask(
			'scripts:dev', 
			'DEVELOPMENT: Concats and compress the JavaScript files.', 
			[ 'concat:development' ]
		);

	// Default task(s).
	grunt.registerTask(
		'dev', 
		'DEVELOPMENT: Compiles all of the assets and copies the files to the build directory.', 
		[ 'clean', 'copy', 'css:dev' ]
	);

	grunt.registerTask(
		'default', 
		'Watches the project for changes automatically builds them.',
		['dev', 'watch']);

};
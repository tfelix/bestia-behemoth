module.exports = function(grunt) {

	// require time grunt.
	require('time-grunt')(grunt);

	var loadConfig = require('load-grunt-config');
	
	var jsFiles = [ 'src/js/bestia.js', 'src/js/core/config.js', 'src/js/core/chat.js',
	    			'src/js/util/net.js', 'src/js/util/pubsub.js', 'src/js/io/message.js',
	    			'src/js/inventory/inventory.js', 'src/js/view/system.pingpong.js', 'src/js/engine/engine.js',
	    			'src/js/chat.js' ];

	loadConfig(grunt, {
		configPath : __dirname + '/tasks/options',
		config : {
			target_dir : 'dist',
			release_dir : 'dist',
			compile_dir : 'build',
			modules_dir : 'build/modules',
			docs_dir : 'docs',
			sourcemap : true,
			filename : 'bestia',
			filelist : jsFiles
		}
	});

	grunt.loadTasks('tasks');

	grunt.registerTask('default', 'Builds the project and packages it for distribution.', [ 'compile' ]);

	grunt.registerTask('dev', 'Testing of the framework.', [ 'test', 'compile', 'connect:dev', 'watch' ]);
	grunt.registerTask('dev-test', 'Testing of the framework.', [ 'test', 'connect:test_test', 'watch' ]);

	grunt.registerTask('compile-js', 'Compiles JS files.', 'bower_concat', 'concat');
	grunt.registerTask('compile-css', 'Compiles CSS files.', [ 'less' ]);
	grunt.registerTask('compile-html', 'Compile HTML files.');

	grunt.registerTask('compile', 'Compile all.', [ 'clean', 'copy', 'compile-css', 'compile-js' ]);

	grunt.registerTask('test', 'Testing of the framework.', [ 'jshint', 'jasmine:all:build' ]);

	//grunt.registerTask('dist', 'Packages the build files for distribution.', function() {});
};
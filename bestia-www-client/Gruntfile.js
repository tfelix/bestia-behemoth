module.exports = function(grunt) {

	// require time grunt.
	require('time-grunt')(grunt);

	var loadConfig = require('load-grunt-config');

	var jsFiles = [ 'src/js/bestia.js', 'src/js/config.js',
	// === CHAT ===
	'src/js/chat/chat_message.js', 'src/js/chat/chat.js', 'src/js/chat/commands/*.js',
	// === BESTIAS ===
	'src/js/bestia/status_point_view_model.js', 'src/js/bestia/bestia_view_model.js',
			'src/js/bestia/bestia_info_view_model.js',
			// === MISC ===
			'src/js/core/bestia/bestias.js', 'src/js/util/net.js', 'src/js/util/pubsub.js', 'src/js/io/connection.js',
			'src/js/io/message.js', 'src/js/inventory/inventory.js', 'src/js/view/system.pingpong.js',
			// === ENGINE ===
			'src/js/engine/plugins/AStar.js', 'src/js/engine/engine.js', 'src/js/engine/states/*.js',
			// === PAGE ===
			'src/js/pages/main.js',
			// === ETC ===
			'src/js/chat.js', 'src/js/main.js' ];

	loadConfig(grunt, {
		configPath : __dirname + '/tasks/options',
		config : {
			source_dir : 'src',
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

	grunt.registerTask('compile-js', 'Compiles JS files.', [ 'bower_concat', 'concat:compile' ]);
	grunt.registerTask('compile-css', 'Compiles CSS files.', [ 'less' ]);
	grunt.registerTask('compile-html', 'Compile HTML files.');

	grunt.registerTask('compile', 'Compile all.', [ 'clean', 'copy', 'compile-js', 'compile-css' ]);

	grunt.registerTask('test', 'Testing of the framework.', [ 'jsonlint', 'jshint', 'jasmine' ]);

	// grunt.registerTask('dist', 'Packages the build files for distribution.',
	// function() {});
};
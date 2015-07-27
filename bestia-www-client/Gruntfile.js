module.exports = function(grunt) {

	var loadConfig = require('load-grunt-config');

	var appFiles = [ 'src/js/bestia.js', 'src/js/config.js',
	// === CHAT ===
	'src/js/chat/chat_message.js', 'src/js/chat/chat.js', 'src/js/chat/commands/*.js',
	// === BESTIAS ===
	'src/js/bestia/status_point_view_model.js', 'src/js/bestia/bestia_view_model.js',
			'src/js/bestia/bestia_info_view_model.js',
			// === MISC ===
			'src/js/core/bestia/bestias.js', 'src/js/util/net.js', 'src/js/util/pubsub.js', 'src/js/io/connection.js',
			'src/js/io/message.js', 'src/js/inventory/inventory.js', 'src/js/view/system.pingpong.js',
			'src/js/util/storage.js',
			// === ENGINE ===
			'src/js/engine/plugins/AStar.js', 'src/js/engine/engine.js', 'src/js/engine/states/*.js',
			'src/js/engine/core/*.js', 'src/js/engine/entity.js', 'src/js/engine/entities/*.js',
			// === PAGE ===
			'src/js/pages/bestia.js',
			// === ETC ===
			'src/js/chat.js', 'src/js/main.js' ];

	var pageFiles = [ 'src/js/bestia.js', 'src/js/util/storage.js', 'src/js/pages/single/all-pages.js' ];

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
			appFilelist : appFiles,
			pageFilelist : pageFiles
		}
	});

	grunt.loadTasks('tasks');

	grunt.registerTask('default', 'Builds the project and packages it for distribution.', [ 'prod' ]);

	grunt.registerTask('compile-js', 'Compile the Javascript.', [ 'copy:dist', 'bower_concat', 'concat:compile']);
	grunt.registerTask('compile', 'Compile all.', [ 'clean', 'copy', 'bower_concat', 'concat:compile', 'less' ]);
	
	grunt.registerTask('compress', 'Compress all files.', []);

	grunt.registerTask('test', 'Testing of the framework.', [ 'jsonlint', 'jshint', 'jasmine' ]);

	grunt.registerTask('dev', 'Compiles for local development of the client.', function(){
		require('time-grunt')(grunt);
		
		grunt.task.run([ 'test', 'compile', 'connect:dev', 'watch' ]);
	});

	// [ 'test', 'clean', 'copy', 'compile-css', 'jshint:prod', 'jsonlint:prod' ]
	grunt.registerTask('prod', 'Compiles the client for production.', function(){
		grunt.task.run([ 'clean', 'compile-js', 'uglify:compile']);
	});
};
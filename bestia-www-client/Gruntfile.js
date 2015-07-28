module.exports = function(grunt) {
	"use strict";

	var loadConfig = require('load-grunt-config');
	
	var version = function(){
		var str = grunt.file.read('../pom.xml').toString();
		var matcher = /<version>(.*?)<\/version>/;
		var data = matcher.exec(str);
		return data[1];
	}();

	var appFiles = [ '<%= tempDir %>/js/util/*.js',
		// === CHAT ===
		'<%= tempDir %>/js/chat/*.js', 
		'<%= tempDir %>/js/chat/commands/*.js',
		// === BESTIAS ===
		'<%= tempDir %>/js/bestia/status_point_view_model.js', 
		'<%= tempDir %>/js/bestia/bestia_view_model.js',
		'<%= tempDir %>/js/bestia/bestia_info_view_model.js',
		// === IO ===
		'<%= tempDir %>/js/io/*.js',
		// === ENGINE ===
		'<%= tempDir %>/js/engine/**/*.js',
		// === INVENTORY ===
		'<%= tempDir %>/js/inventory/*.js',
		// === PAGE ===
		'<%= tempDir %>/js/pages/bestia.js',
		// === MAIN ===
		'<%= tempDir %>/js/main.js' ];

	var pageFiles = [ 'src/js/bestia.js', 'src/js/util/storage.js', 'src/js/pages/single/all-pages.js' ];

	loadConfig(grunt, {
		configPath : __dirname + '/tasks/options',
		config : {
			sourceDir : 'src',
			tempDir : 'build_temp',
			buildDir : 'build',
			distDir : 'dist',
			sourcemap : true,
			appFilelist : appFiles,
			pageFilelist : pageFiles,
			version: version
		}
	});

	grunt.loadTasks('tasks');

	grunt.registerTask('default', 'Builds the project and packages it for distribution.', [ 'prod' ]);


	grunt.registerTask('compile-js', 'Compile the Javascript.', ['preprocess:prod', 'test:prod', 'concat', 'uglify']);
	grunt.registerTask('compile', 'Compile all.', [ 'clean', 'copy', 'bower_concat', 'concat:compile', 'less' ]);

	grunt.registerTask('test:config', 'Testing of the Grunt config.', ['jsonlint:config', 'jshint:config']);
	grunt.registerTask('test:prod', 'Testing for production.', [ 'jsonlint:prod', 'jshint:prod', 'jasmine' ]);

	grunt.registerTask('dev', 'Compiles for local development of the client.', function(){
		require('time-grunt')(grunt);
		grunt.log.writeln("Compiling bestia-www-client: " + version + " (development)");
		
		grunt.task.run([ 'test', 'compile', 'connect:dev', 'watch' ]);
	});

	grunt.registerTask('prod', 'Compiles the client for production.', function(){
		grunt.log.writeln("Compiling bestia-www-client: " + version + " (production)");
		
		grunt.task.run('clean');
		
		// Compile HTML
		grunt.task.run(['preprocess:htmlprod']);
		
		// Compile JS
		grunt.task.run(['bower_concat', 'compile-js', 'clean:temp']);
	});
};
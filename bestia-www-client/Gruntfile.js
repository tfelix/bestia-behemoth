module.exports = function(grunt) {
	"use strict";

	var loadConfig = require('load-grunt-config');

	var version = function() {
		var str = grunt.file.read('../pom.xml').toString();
		var matcher = /<version>(.*?)<\/version>/;
		var data = matcher.exec(str);
		return data[1];
	}();
	
	var appFiles = [ '<%= tempDir %>/js/behemoth.js', '<%= tempDir %>/js/util/*.js',
	         		// === CHAT ===
	         		'<%= tempDir %>/js/chat/*.js', '<%= tempDir %>/js/chat/commands/*.js',
	         		// === BESTIAS ===
	         		'<%= tempDir %>/js/bestia/*.js',
	         		// === IO ===
	         		'<%= tempDir %>/js/io/*.js',
	         		// === ENGINE ===
	         		'<%= tempDir %>/js/engine/engine.js', '<%= tempDir %>/js/engine/core/*.js',
	         		'<%= tempDir %>/js/engine/entities/*.js', '<%= tempDir %>/js/engine/plugins/*.js',
	         		'<%= tempDir %>/js/engine/states/*.js' ];
	
	// Add the intro, outro 
	var appFilelistShimed = appFiles.slice();
	appFilelistShimed.unshift('<%= tempDir %>/js/intro.js');
	appFilelistShimed.push('<%= tempDir %>/js/pages/app.js');
	appFilelistShimed.push('<%= tempDir %>/js/main.js');
	appFilelistShimed.push('<%= tempDir %>/js/outro.js');
	
	var pageFilelistShimed = ['<%= tempDir %>/js/intro.js', '<%= tempDir %>/js/behemoth.js', '<%= tempDir %>/js/outro.js'];

	loadConfig(grunt, {
		configPath : __dirname + '/tasks/options',
		config : {
			sourceDir : 'src',
			tempDir : 'build_temp',
			buildDir : 'build',
			distDir : 'dist',
			sourcemap : true,
			appFilelist : appFiles,
			appFilelistShimed : appFilelistShimed,
			pageFilelistShimed : pageFilelistShimed,
			version : version
		}
	});

	grunt.loadTasks('tasks');

	grunt.registerTask('default', 'Builds the project and packages it for distribution.', [ 'prod' ]);

	
	grunt.registerTask('test:config', 'Testing of the Grunt config.', [ 'jsonlint:config', 'jshint:config' ]);
	

	grunt.registerTask('dev', 'Compiles for local development of the client.', function() {
		require('time-grunt')(grunt);
		grunt.log.writeln("====================================================================");
		grunt.log.writeln("Compiling bestia-www-client: " + version + " (development)");
		grunt.log.writeln("====================================================================");

		// Prepare build.
		grunt.task.run('test:config');
		grunt.task.run('clean');

		// Compile HTML
		grunt.task.run('preprocess:htmldev');
		
		// Compile CSS
		grunt.task.run('less');
		
		// Prepare Assets
		grunt.task.run('copy:dist');
		grunt.task.run('copy:gamedata');

		// Compile JS library
		grunt.task.run('bower_concat');

		// Compile JS (Main application)
		grunt.task.run([ 'preprocess:dev', 'jsonlint', 'jshint', 'concat']);
		
		// Start server and watch tasks.
		grunt.task.run(['connect', 'watch']);
	});

	
	grunt.registerTask('prod', 'Compiles the client for production.', function() {
		grunt.log.writeln("====================================================================");
		grunt.log.writeln("Compiling bestia-www-client: " + version + " (production)");
		grunt.log.writeln("====================================================================");

		// Prepare build
		grunt.task.run('test:config');
		grunt.task.run('clean');

		// Compile HTML
		grunt.task.run('preprocess:htmlprod');
		
		// Prepare Assets
		grunt.task.run('copy:dist');
		grunt.task.run('copy:gamedata');
		
		// Compile CSS
		grunt.task.run('less');

		// Compile JS
		grunt.task.run('bower_concat');
		
		// Before we can start the testing process we need to 

		grunt.task.run([ 'preprocess:prod', 'jsonlint', 'jshint', 'jasmine' ]);

		grunt.task.run([ 'concat', 'uglify' ]);
	});
};
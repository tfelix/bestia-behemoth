module.exports = function(grunt) {
	"use strict";

	var loadConfig = require('load-grunt-config');

	/**
	 * Full version of the app (e.g. beta-1.2.4).
	 */
	var version = function() {
		var str = grunt.file.read('../pom.xml').toString();
		var matcher = /<version>(.*?)<\/version>/;
		var data = matcher.exec(str);
		return data[1];
	}();
	
	/**
	 * Short, number version only without alpha-, beta- etc.
	 * Some plugins crash on non numeric versions.
	 */
	var versionOnly = function() {
		return version.replace(/([^0-9\.]+)/gi, '');
	}();
	
	var appFiles = [ '<%= tempDir %>/js/behemoth.js', '<%= tempDir %>/js/util/*.js',
	         		// === CHAT ===
	         		'<%= tempDir %>/js/chat/*.js', 
	         		'<%= tempDir %>/js/chat/commands/realtime_command.js',
	         		'<%= tempDir %>/js/chat/commands/basic_command.js',
	         		'<%= tempDir %>/js/chat/commands/clear_command.js',
	         		'<%= tempDir %>/js/chat/commands/debug_command.js',
	         		'<%= tempDir %>/js/chat/commands/help_command.js',
	         		'<%= tempDir %>/js/chat/commands/mode_guild_command.js',
	         		'<%= tempDir %>/js/chat/commands/mode_party_command.js',
	         		'<%= tempDir %>/js/chat/commands/mode_public_command.js',
	         		'<%= tempDir %>/js/chat/commands/mode_whisper_command.js',
	         		// === BESTIAS ===
	         		'<%= tempDir %>/js/bestia/*.js',
	         		// === IO ===
	         		'<%= tempDir %>/js/io/*.js',
	         		// === INVENTORY ===
	         		'<%= tempDir %>/js/inventory/*.js',
	         		// === ATTACKS ===
	         		'<%= tempDir %>/js/attack/*.js',
	         		// === ENGINE ===
	         		'<%= tempDir %>/js/engine/engine.js', '<%= tempDir %>/js/engine/core/*.js',
	         		'<%= tempDir %>/js/engine/entities/*.js', '<%= tempDir %>/js/engine/plugins/*.js',
	         		'<%= tempDir %>/js/engine/controller/*.js', '<%= tempDir %>/js/engine/cg/*.js',
	         		'<%= tempDir %>/js/engine/states/*.js',
	         		'<%= tempDir %>/js/engine/fx/*.js',
	         		// === LIBS ===
	         		'<%= tempDir %>/lib/js/visibility_polygon.js'];
	
	// Add the intro, outro 
	var appFilelistShimed = appFiles.slice();
	appFilelistShimed.unshift('<%= tempDir %>/js/intro.js');
	appFilelistShimed.push('<%= tempDir %>/js/pages/app.js');
	appFilelistShimed.push('<%= tempDir %>/js/main.js');
	appFilelistShimed.push('<%= tempDir %>/js/outro.js');
	
	var pageFilelistShimed = ['<%= tempDir %>/js/intro.js', 
	                          '<%= tempDir %>/js/behemoth.js',
	                          '<%= tempDir %>/js/util/storage.js',
	                          '<%= tempDir %>/js/outro.js'];

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
	
	grunt.registerTask('setupversion', 'Transfering the current POM.xml version to Grunt.', function(){
		grunt.log.writeln("====================================================================");
		grunt.log.writeln("Updating bestia-www-client versions to: " + version);
		grunt.log.writeln("====================================================================");
		
		var opt = {encoding: 'UTF-8'};
		
		grunt.log.writeln("Updating bower.json version...");
		var filedata = grunt.file.read('bower.json', opt);
		filedata = filedata.replace(/"version":\s?".+?",/, '"version": "'+versionOnly+'",');
		grunt.file.write('bower.json', filedata, opt);
		
		grunt.log.writeln("Updating package.json version...");
		filedata = grunt.file.read('package.json', opt);
		filedata = filedata.replace(/"version":\s?".+?",/, '"version": "'+versionOnly+'",');
		grunt.file.write('package.json', filedata, opt);
		
		grunt.log.writeln("Updating src/js/behemoth.js version...");
		filedata = grunt.file.read('src/js/behemoth.js', opt);
		filedata = filedata.replace(/VERSION[\s+]?:[\s+]?["'].+?["'],/, 'VERSION: \''+version+'\',');
		grunt.file.write('src/js/behemoth.js', filedata, opt);
	});

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
	
	grunt.registerTask('test', 'Compiles the client in order to perform a full testrun.', function() {
		grunt.log.writeln("====================================================================");
		grunt.log.writeln("Performing Unittests: bestia-www-client: " + version);
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

		grunt.task.run([ 'preprocess:prod', 'jsonlint', 'jshint', 'jasmine' ]);
	});
};
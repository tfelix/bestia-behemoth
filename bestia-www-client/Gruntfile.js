module.exports = function(grunt) {
	"use strict";

	var loadConfig = require('load-grunt-config');

	var version = function() {
		var str = grunt.file.read('../pom.xml').toString();
		var matcher = /<version>(.*?)<\/version>/;
		var data = matcher.exec(str);
		return data[1];
	}();


	var pageFiles = [ 'src/js/bestia.js', 'src/js/util/storage.js', 'src/js/pages/single/all-pages.js' ];

	loadConfig(grunt, {
		configPath : __dirname + '/tasks/options',
		config : {
			sourceDir : 'src',
			tempDir : 'build_temp',
			buildDir : 'build',
			distDir : 'dist',
			sourcemap : true,
			appFilelist : null,
			pageFilelist : pageFiles,
			version : version
		}
	});

	grunt.loadTasks('tasks');

	grunt.registerTask('default', 'Builds the project and packages it for distribution.', [ 'prod' ]);


	grunt.registerTask('test:config', 'Testing of the Grunt config.', [ 'jsonlint:config', 'jshint:config' ]);

	grunt.registerTask('dev', 'Compiles for local development of the client.', function() {
		require('time-grunt')(grunt);
		grunt.log.writeln("Compiling bestia-www-client: " + version + " (development)");

		//grunt.task.run([ 'test', 'compile', 'connect:dev', 'watch' ]);
	});

	grunt.registerTask('prod', 'Compiles the client for production.', function() {
		grunt.log.writeln("==================================");
		grunt.log.writeln("Compiling bestia-www-client: " + version + " (production)");
		grunt.log.writeln("==================================");

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

		grunt.config.set('appFilelist', appFiles);

		grunt.task.run('test:config');
		
		grunt.task.run('clean');

		// Compile HTML
		grunt.task.run('preprocess');
		
		grunt.task.run('copy:dist');
		grunt.task.run('copy:gamedata');

		// Compile JS
		grunt.task.run('bower_concat');

		grunt.task.run([ 'preprocess:prod', 'jsonlint:prod', 'jshint:prod', 'jasmine' ]);

		appFiles.unshift('<%= tempDir %>/intro.js');
		appFiles.push('<%= tempDir %>/outro.js');
		appFiles.push('<%= tempDir %>/pages/app.js');

		grunt.config.set('appFilelist', appFiles);

		grunt.task.run([ 'concat', 'uglify' ]);
	});
};
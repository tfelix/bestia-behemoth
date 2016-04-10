module.exports = function(grunt) {
	"use strict";

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

	var loadConfig = require('load-grunt-config');
	loadConfig(grunt, {
		configPath : __dirname + '/tasks/options',
		config : {
			sourceDir : 'src',
			tempDir : 'build_temp',
			buildDir : 'build',
			distDir : 'dist',
			sourcemap : true,
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
		grunt.task.run([ 'preprocess:dev', 'jsonlint', 'jshint', 'rollup']);
		
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
module.exports = {
	src : {
		src : [ 'src/js/**/*.js', '!src/js/lib/**', '!src/js/engine/plugins/**', '!src/js/intro.js', '!src/js/outro.js' ],
		options : {
			jshintrc : '.jshintrc'
		}
	},

	prod : {
		src : [ '<%= tempDir %>/js/**/*.js', '!<%= tempDir %>/js/engine/plugins/**', '!<%= tempDir %>/js/intro.js',
				'!<%= tempDir %>/js/outro.js' ],
		options : {
			jshintrc : '.jshintrc'
		}
	},

	config : {
		src : [ 'Gruntfile.js', 'tasks/**/*.js' ]
	}
};
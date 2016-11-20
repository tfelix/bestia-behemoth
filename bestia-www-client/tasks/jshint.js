module.exports = {
	source : {
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
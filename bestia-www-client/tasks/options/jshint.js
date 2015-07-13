module.exports = {
	src : {
		src : [ 'src/js/**/*.js', '!src/js/lib/**', '!src/js/engine/plugins/**', '!src/js/intro.js', '!src/js/outro.js' ],
		options : {
			jshintrc : '.jshintrc'
		}
	},
	
	tooling: {
        src: [
            'Gruntfile.js',
            'tasks/**/*.js'
        ]
    }
};
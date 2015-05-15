module.exports = {
	src : {
		src : [ 'src/js/**/*.js', '!src/js/lib/**', '!src/js/intro.js', '!src/js/outro.js' ],
		options : {
			jshintrc : '.jshintrc'
		}
	},
	
	tooling: {
        src: [
            'Gruntfile.js',
            'tasks/**/*.js'
        ]
        //options: { jshintrc: 'tasks/.jshintrc' }
    }
};
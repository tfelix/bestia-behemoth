var babel = require('rollup-plugin-babel');

module.exports = {
	rollup : {
		options : {
			sourceMap : true,
			format : 'iife',
			moduleName : 'bestia',
			plugins : function() {
				return [ babel({
					sourceMaps : true,
					babelrc : false,
					exclude : 'node_modules/**',
					presets: [['es2015', {modules: false}]],
					plugins: ['external-helpers']
				}) ];
			}
		},
		files : {
			'<%= buildDir %>/js/behemoth.js' : [ '<%= tempDir %>/js/main.js' ]
		}
	}
};
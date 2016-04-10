var babel = require('rollup-plugin-babel');

module.exports = {
	rollup : {
		options : {
			sourceMap : true,
			format: 'iife',
			moduleName: 'bestia',
			plugins : [ babel({
				sourceMaps: true,
				babelrc: false,
				exclude: 'node_modules/**',
				presets: ["es2015-rollup"]
			}) ]
		},
		files : {
			'<%= buildDir %>/js/behemoth.js' : [ '<%= tempDir %>/js/main.js' ]
		}
	}
};
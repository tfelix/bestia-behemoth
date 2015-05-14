module.exports = {
	compile : {
		options : {
			paths : [ 'src/css/less/**/' ],
			sourceMap : true,
			sourceMapBasepath: '<%= compile_dir %>/css',
			
		},
		files : {
			'build/css/app.css' : 'src/css/less/main.less'
		}
	}
};
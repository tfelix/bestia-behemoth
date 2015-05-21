module.exports = {
	compile : {
		options : {
			paths : [ 'src/css/less/**/' ],
			sourceMap : true,
			sourceMapURL: '/css/app.css.map',
		},
		files : {
			'build/css/app.css' : 'src/css/less/main.less',
			'build/css/login.css' : 'src/css/less/login.less'
		}
	}
};
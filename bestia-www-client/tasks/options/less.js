module.exports = {
	development : {
		options : {
			paths : [ 'src/css/less/**/' ]
		},
		files : {
			'build/css/app.css' : 'src/css/less/main.less'
		}
	}
};
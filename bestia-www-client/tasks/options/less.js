module.exports = {
	compile : {
		options : {
			paths : [ '<%= sourceDir %>/css/less/**/' ],
			sourceMap : true,
			sourceMapURL: '/css/app.css.map',
		},
		files : {
			'<%= buildDir %>/css/app.css' : '<%= sourceDir %>/css/less/main.less',
			'<%= buildDir %>/css/login.css' : '<%= sourceDir %>/css/less/login.less'
		}
	}
};
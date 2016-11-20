module.exports = {
	compile : {
		options : {
			paths : [ '<%= sourceDir %>/css/less/**/' ],
			sourceMap : true,
			sourceMapURL: '/css/app.css.map',
		},
		files : {
			'<%= buildDir %>/css/game/main.css' : '<%= sourceDir %>/css/less/main.less',
			'<%= buildDir %>/css/pages/login.css' : '<%= sourceDir %>/css/less/pages/login.less'
		}
	}
};
module.exports = {

	options : {
		sourceMap : true
	},

	compile : {
		src : [ '<%= appFilelistShimed %>' ],
		dest : '<%= buildDir %>/js/behemoth.js'

	},

	pageCreate : {
		src : [ '<%= tempDir %>/js/pages/all-pages.js',
				'<%= tempDir %>/js/pages/create.js' ],
		dest : '<%= buildDir %>/js/create.js'

	},

	pageLogin : {
		src : [ '<%= tempDir %>/js/pages/all-pages.js',
				'<%= tempDir %>/js/pages/login.js' ],
		dest : '<%= buildDir %>/js/login.js'
	}
};
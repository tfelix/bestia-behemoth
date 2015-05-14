module.exports = {

	chat : {
		src : [ 'src/js/core/chat/models.js', 'src/js/core/chat/chat.js', 'src/js/core/chat/command/*.js' ],
		desc : '<%= modules_dir %>/chat.js'
	},

	compile : {
		options : {
			sourceMap : true
		},
		// Custom scripts. Order is important!
		src : [ '<%= filelist %>' ],
		dest : '<%= compile_dir %>/js/<%= filename %>.js'
	}

};
module.exports = {
	options : {
		inline : true,
		context : {
			DEBUG : true
		}
	},

	development : {
		src : ['']
	},

	production : {
		src : [''],
		options : {
			context : {
				DEBUG : false
			}
		}
	}
};
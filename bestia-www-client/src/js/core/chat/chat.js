// Add startsWith functionality to string prototype.
/*jshint -W121 */
if (typeof String.prototype.startsWith != 'function') {
	String.prototype.startsWith = function(str) {
		return this.slice(0, str.length) == str;
	};
}

// TODO Das hier kann man geschickter lösen.
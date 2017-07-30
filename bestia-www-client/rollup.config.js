import json from 'rollup-plugin-json';
import babel from 'rollup-plugin-babel';
import resolve from 'rollup-plugin-node-resolve';
import commonjs from 'rollup-plugin-commonjs';
import uglify from 'rollup-plugin-uglify';
import replace from 'rollup-plugin-replace';

export default [{
  entry: 'src/js/main.js',
  dest: 'build/js/behemoth.js',
  format: 'iife',
  sourceMap: true,
  plugins: [
    json(),
    resolve({
      jsnext: true,
      main: true,
      browser: true,
      preferBuiltins: false
    }),
    commonjs({
      namedExports: {
        'node_modules\zepto\dist\zepto.js': ['Zepto']
      }
    }),
    babel({
      exclude: [
        'node_modules/**',
        '*.less'
      ]
    }),
    replace({
      exclude: 'node_modules/**',
      ENV: JSON.stringify(process.env.NODE_ENV || 'development'),
    }),
    (process.env.NODE_ENV === 'production' && uglify())
  ]
}, {
  entry: 'src/js/pages/login.js',
  dest: 'build/js/login.js',
  format: 'iife',
  sourceMap: true,
  plugins: [
    json(),
    resolve({jsnext: true, main: true}),
    commonjs()
  ]
}];
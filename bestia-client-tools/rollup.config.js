import json from 'rollup-plugin-json';
import babel from 'rollup-plugin-babel';
import resolve from 'rollup-plugin-node-resolve';
import commonjs from 'rollup-plugin-commonjs';
import uglify from 'rollup-plugin-uglify';
import replace from 'rollup-plugin-replace';

export default [{
  input: 'src/js/main.js',
  output: {
    file: 'build/js/tools.js',
    format: 'iife'
  },
  sourcemap: true,
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
    })
  ]
}
];
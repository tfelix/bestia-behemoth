import json from 'rollup-plugin-json';
import babel from 'rollup-plugin-babel';
import resolve from 'rollup-plugin-node-resolve';
import commonjs from 'rollup-plugin-commonjs';
import uglify from 'rollup-plugin-uglify';
import replace from 'rollup-plugin-replace';

export default [{
  input: 'src/js/main.js',
  output: {
    file: 'build/js/behemoth.js',
    format: 'iife'
  },
  sourcemap: 'inline',
  plugins: [
    json(),
    replace({
      exclude: 'node_modules/**',
      ENV: JSON.stringify(process.env.NODE_ENV || 'development'),
    }),
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
      babelrc: false,
      presets: [['es2015', { modules: false }]],
      plugins: ["external-helpers"],
      exclude: [
        'node_modules/**',
        '*.less'
      ]
    }),
    (process.env.NODE_ENV === 'production' && uglify())
  ]
}, {
  input: 'src/js/pages/login.js',
  output: {
    file: 'build/js/login.js',
    format: 'iife'
  },
  sourcemap: true,
  plugins: [
    json(),
    resolve({ jsnext: true, main: true }),
    commonjs()
  ]
},
{
  input: 'src/js/pages/register.js',
  output: {
    file: 'build/js/register.js',
    format: 'iife'
  },
  sourcemap: true,
  plugins: [
    json(),
    resolve({ jsnext: true, main: true }),
    commonjs()
  ]
}];
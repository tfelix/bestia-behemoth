import json from 'rollup-plugin-json';
import babel from 'rollup-plugin-babel';
import less from 'rollup-plugin-less';
import resolve from 'rollup-plugin-node-resolve';
import commonjs from 'rollup-plugin-commonjs';

export default {
  entry: 'src/js/main.js',
  dest: 'build/js/behemoth.js',
  format: 'iife',
  sourceMap: true,
  plugins: [
    json(),
    less({ output: './build/css/main.css' }),
    resolve({
      jsnext: true,
      main: true,
      browser: true
    }),
    commonjs(),
    babel({
      exclude: [
        'node_modules/**',
        '*.less'
      ]
    })
  ]
};
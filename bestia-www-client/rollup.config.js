import json from 'rollup-plugin-json';
import babel from 'rollup-plugin-babel';
import less from 'rollup-plugin-less';

export default {
  entry: 'src/js/main.js',
  dest: 'build/js/behemoth.js',
  format: 'iife',
  sourceMap: true,
  plugins: [less(), json(), babel({
    exclude: [
      'node_modules/**',
      '*.less'
      ]
  })]
};
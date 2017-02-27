import json from 'rollup-plugin-json';
import babel from 'rollup-plugin-babel';
import less from 'rollup-plugin-less';

export default {
  entry: 'src/js/main.js',
  format: 'es',
  sourceMap: true,
  plugins: [json(), babel(), less()],
  dest: 'build/js/behemoth.js'
};
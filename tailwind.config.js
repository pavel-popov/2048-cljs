const colors = require('tailwindcss/colors')

module.exports = {
  theme: {
    extend: {
      colors: {
        'light-blue': colors.lightBlue,
        cyan: colors.cyan,
      },
    },
  },
  purge: [
    './target/**/*.html',
    './src/**/*.cljs',
  ],
  variants: {},
  plugins: [],
}

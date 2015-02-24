// Include gulp
var gulp = require('gulp');

// Include plugins
var concat = require('gulp-concat');
var uglify = require('gulp-uglify');
var rename = require('gulp-rename');

// Concatenate & Without Minify JS
gulp.task('scriptsWM', function() {
    return gulp.src('src/*.js')
        .pipe(concat('preload.js'))
        .pipe(gulp.dest('dist'));
});

// Concatenate & Minify JS
gulp.task('scripts', function() {
    return gulp.src('src/*.js')
        .pipe(concat('preload.js'))
        .pipe(rename({suffix: '.min'}))
        .pipe(uglify())
        .pipe(gulp.dest('dist'));
});

// Default Task
gulp.task('default', ['scriptsWM', 'scripts']);
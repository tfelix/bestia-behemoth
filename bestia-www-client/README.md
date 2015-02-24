==== Simple Website Skeleton ====

This template provides tools and scripts to compile websites for best web performance.

== Simple ==

Development on a new project should start as fast a possible so this template
was designed to work out of the box on Microsoft Windows PCs. Java is the only
requirement (but is installed on most of the PCs anyway). Even the .gitignore file
is already present. So you can start developing in Eclipse right away.

Where it makes sense convention over configuration is used. Example? If you use
a library which needs a custom CSS file, just drop it in /css/lib. It will automatically be included in the main css file.



== Modern and fast. ==

It uses bootstrap.css as CSS template, requirejs AMD loader, jquery and less CSS compilation.
One command builds the complete website.


== Small. ==

Images are optimized with up to date compression tools (optimizing png and jpg images).


=== Usage ===

Prerequisite:

Java must be installed! Please check if you can invoke the java -version command from your commandline.

Now download the website template, navigate to the directory via a commandline and call the build.cmd script.
This is it! The build website will be put into the www-build folder.

For an example how to use the AMD loader go to http://requirejs.org/ or look at the provided template example.

== Notice ==

Some tools included in this script collection are licenced under different conditions then this project. Please keep this in mind!
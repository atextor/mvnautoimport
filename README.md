Maven Auto Import
=================

Very simple Eclipse Plug-in, that downloads Java dependencies for you. No manual copying of jars to your lib folder, no writing POMs nor editing build.xmls or ivy.xmls.

It works as follows:
 * You select the name of the class that you need. Fully qualified or simple name, e.g. "Appender".
 * You right-click the text and select "Source"-"Automatically add Maven Dependency..." (or press the shortcut key - default is Ctrl+6)
 * Select the library you need from all libraries that contain a class with that name.
 * The library is automatically downloaded and added to the lib folder or your project, and added to the class path.

![Screenshot](https://github.com/atextor/mvnautoimport/raw/master/1.png)
![Screenshot](https://github.com/atextor/mvnautoimport/raw/master/2.png)

Note: The default maximum libraries displayed in the selection list is 20. If you select class names that appear in many libraries, such as "Logger", you need to increase this number in the plugin settings. Change the "rows" parameter
inside the Maven repository URL to something higher.

Author: Andreas Textor <textor.andreas@googlemail.com>

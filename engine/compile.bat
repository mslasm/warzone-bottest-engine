rem for debug build use "compile.bat -g"

dir /b /s *.java>sources.txt

rmdir /s /q bin
mkdir bin

javac -d bin @sources.txt -cp lib/java-json.jar;lib/guava-23.0.jar %1

rm sources.txt
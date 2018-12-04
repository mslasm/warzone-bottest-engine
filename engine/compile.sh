#!/bin/bash

# for debug build use `compile.sh -g`

rm -rf bin
mkdir bin

javac -sourcepath src/ -d bin/ -cp "lib/java-json.jar:lib/guava-23.0.jar" `find src/ -name '*.java'` $1
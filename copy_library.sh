#!/bin/zsh

./gradlew installDist

mkdir clients
mkdir clients/c
mkdir clients/1
mkdir clients/2
mkdir clients/3
mkdir clients/4

rm -rf clients/c/*
rm -rf clients/1/*
rm -rf clients/2/*
rm -rf clients/3/*
rm -rf clients/4/*

cp -r build/install/library/* clients/c/
cp -r build/install/library/* clients/1/
cp -r build/install/library/* clients/2/
cp -r build/install/library/* clients/3/
cp -r build/install/library/* clients/4/

echo "Copy successful!"

return 0


#!/bin/zsh

./gradlew installDist

mkdir -p clients/c
mkdir -p clients/0
mkdir -p clients/1
mkdir -p clients/2
mkdir -p clients/3

rm -rf clients/c/*
rm -rf clients/0/*
rm -rf clients/1/*
rm -rf clients/2/*
rm -rf clients/3/*

cp -r build/install/library/* clients/c/
cp -r build/install/library/* clients/0/
cp -r build/install/library/* clients/1/
cp -r build/install/library/* clients/2/
cp -r build/install/library/* clients/3/

echo "Copy successful!"

return 0


#!/bin/bash
mkdir buchiAutomizer
cp -a ../../source/BA_SiteRepository/target/products/CLI-E3/linux/gtk/x86_64/* buchiAutomizer/
cp LICENSE* buchiAutomizer/
cp ../../examples/toolchains/BuchiAutomizerCWithBlockEncoding.xml buchiAutomizer/
cp BuchiAutomizerDefaultSettings buchiAutomizer/
cp buchiAutomizer.sh buchiAutomizer/
cp Ultimate.ini buchiAutomizer/
cp README buchiAutomizer/
zip UltimateCommandline.zip -r buchiAutomizer/*

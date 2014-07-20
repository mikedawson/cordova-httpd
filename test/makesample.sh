#!/bin/bash

WORKINGDIR=$(pwd)
TARGETDIR=$WORKINGDIR/../../cordova-testhttp
PLUGINDIR=$(pwd)/../

if [ -e $TARGETDIR ]; then
    rm -rf $TARGETDIR
fi

if [ ! -e $TARGETDIR ]; then
    mkdir -p $TARGETDIR
fi


cd $TARGETDIR
cordova create testhttp com.rjfun.cordova.httpd.tester testhttp
cd testhttp
cordova platform add android
cordova plugin add org.apache.cordova.file
cordova plugin add $PLUGINDIR

echo "Created cordova project in $(pwd)"

cp $PLUGINDIR/test/index.html $TARGETDIR/testhttp/www/index.html

cd $WORKINGDIR



#!/bin/bash

_script="$(readlink -f ${BASH_SOURCE[0]})"
## Delete last component from $_script ##
RP_DIR="$(dirname $_script)"

CLASSPATH=$RP_DIR/reset-password.jar:webapps/livecluster/WEB-INF/lib/*

$JAVA_HOME/bin/java -cp $CLASSPATH com.datasynapse.gridserver.tools.RootPasswordUpdater $*

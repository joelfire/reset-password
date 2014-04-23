@echo off
setLocal EnableDelayedExpansion

set RP_DIR=%~dp0
set CLASSPATH=%RP_DIR%\reset-password.jar;webapps\livecluster\WEB-INF\lib\*

java -cp %CLASSPATH% com.datasynapse.gridserver.tools.RootPasswordUpdater %*

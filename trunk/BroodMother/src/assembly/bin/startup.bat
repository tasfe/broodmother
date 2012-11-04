@echo off
%~d0
cd %~dp0
start java  -Xmx128m -Xms128m  -jar ../lib/picman-agent-0.0.1-SNAPSHOT.jar %1
exit
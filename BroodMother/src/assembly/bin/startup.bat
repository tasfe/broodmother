@echo off
%~d0
cd %~dp0
start java  -Xmx256m -Xms128m  -jar ../lib/BroodMother-1.0.jar %1
exit
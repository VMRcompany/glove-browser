@echo off
setlocal
set ROOT=%~dp0..
set JDK=%ROOT%\tools\j2me\jdk8
set LIB=%ROOT%\tools\j2me\lib
set SRC=%ROOT%\midlet\src
set TMP=%ROOT%\midlet\tmpclasses
set DIST=%ROOT%\docs\midlet
set JAVAC=%JDK%\bin\javac.exe
set JAR=%JDK%\bin\jar.exe

if not exist "%JAVAC%" (
  echo JDK 8 not found at %JDK%
  exit /b 1
)
if not exist "%LIB%\midpapi20.jar" (
  echo Missing %LIB%\midpapi20.jar
  exit /b 1
)

rmdir /s /q "%TMP%" 2>nul
mkdir "%TMP%"
mkdir "%DIST%" 2>nul

echo Compiling MIDlet with JDK 8 / MIDP 2.0...
"%JAVAC%" -bootclasspath "%LIB%\cldcapi11.jar;%LIB%\midpapi20.jar" -source 1.3 -target 1.3 -d "%TMP%" "%SRC%\com\glove\browser\GloveMidlet.java" "%SRC%\com\glove\browser\HttpFetcher.java" "%SRC%\com\glove\browser\HtmlLite.java"
if errorlevel 1 exit /b 1

echo Creating GloveBrowser.jar...
"%JAR%" cfm "%DIST%\GloveBrowser.jar" "%ROOT%\midlet\MANIFEST.MF" -C "%TMP%" .

for %%I in ("%DIST%\GloveBrowser.jar") do set SIZE=%%~zI

> "%DIST%\GloveBrowser.jad" (
  echo MIDlet-Name: Glove Browser
  echo MIDlet-Version: 1.0.0
  echo MIDlet-Vendor: VMR Company
  echo MIDlet-Description: Glove Browser for Nokia / Java MIDP phones
  echo MIDlet-Info-URL: https://glove.mineholde.pro/
  echo MIDlet-Jar-URL: GloveBrowser.jar
  echo MIDlet-Jar-Size: %SIZE%
  echo MIDlet-1: Glove Browser, , com.glove.browser.GloveMidlet
  echo MicroEdition-Configuration: CLDC-1.1
  echo MicroEdition-Profile: MIDP-2.0
)

echo.
echo OK: %DIST%\GloveBrowser.jar  (%SIZE% bytes^)
echo OK: %DIST%\GloveBrowser.jad
dir "%DIST%\GloveBrowser.*"
endlocal

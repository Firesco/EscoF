@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "ESCO_WORKERS=auto"
:parse
if "%~1"=="" goto run
if /i "%~1"=="--help" goto help
if /i "%~1"=="--workers" goto workers
>&2 echo Unknown option. Use start-escof.bat --help
exit /b 2
:workers
if "%~2"=="" goto badworkers
set "ESCO_WORKERS=%~2"
if /i "%ESCO_WORKERS%"=="auto" goto accepted
if /i "%ESCO_WORKERS%"=="all" goto accepted
if "%ESCO_WORKERS%"=="-1" goto accepted
powershell.exe -NoLogo -NoProfile -NonInteractive -Command "$v=$env:ESCO_WORKERS; if ($v -match '^[0-9]{1,5}$' -and [int]$v -le 32767) { exit 0 } else { exit 1 }" >nul 2>&1
if errorlevel 1 goto badworkers
:accepted
shift
shift
goto parse
:badworkers
>&2 echo --workers requires auto, all, -1, or a whole number from 0 to 32767.
exit /b 2
:help
echo Usage: start-escof.bat [--workers auto^|all^|0..32767]
echo Example: start-escof.bat --workers all
echo Default auto leaves two available processors for other work.
echo All uses the processor count reported by the JVM as the worker limit.
echo Zero runs Esco calculations on the caller. Smaller jobs use fewer workers.
echo Requires Java 25. This is a compute-pool limit, not the server thread count.
exit /b 0
:run
pushd "%~dp0"
if errorlevel 1 exit /b 1
set "ESCO_JAVA=java"
if defined JAVA_HOME set "ESCO_JAVA=%JAVA_HOME%\bin\java.exe"
if not exist "escof-26.2-0.7.0.jar" (
    >&2 echo Missing escof-26.2-0.7.0.jar next to this BAT file.
    popd
    exit /b 1
)
echo Starting EscoF - Developed By Firesco - 26.2 / 0.7.0 with workers=%ESCO_WORKERS%.
"%ESCO_JAVA%" -Xms2G -Xmx4G -Descof.workers=%ESCO_WORKERS% -jar "escof-26.2-0.7.0.jar" --nogui
set "ESCO_EXIT=%ERRORLEVEL%"
popd
exit /b %ESCO_EXIT%

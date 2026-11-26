@echo off
setlocal

if not defined JAVA_HOME (
    if exist "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot" (
        set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
    )
)

set "PATH=%JAVA_HOME%\bin;%USERPROFILE%\.maven\apache-maven-3.9.6\bin;%PATH%"
rem Keep Maven from exhausting native memory on smaller server/development machines.
set "MAVEN_OPTS=-Xms128m -Xmx512m -XX:MaxMetaspaceSize=256m"

echo ===================================================
echo   Building HyroCosmetics (Paper Plugin)
echo   Java: %JAVA_HOME%
echo ===================================================

if exist "..\HyroCore\pom.xml" (
    echo Installing sibling HyroCore API...
    pushd "..\HyroCore"
    call mvn -B -DskipTests install
    if errorlevel 1 (
        echo Failed to install HyroCore API.
        popd
        exit /b 1
    )
    popd
)

call mvn -B clean package
if errorlevel 1 (
    echo HyroCosmetics build failed.
    exit /b 1
)

echo.
echo Build complete! Check target\ folder for HyroCosmetics-1.1.0.jar.

if exist "d:\Minecraft server\plugins" (
    echo.
    echo Deploying to local Minecraft server: d:\Minecraft server\plugins ...
    copy /y "target\HyroCosmetics-1.1.0.jar" "d:\Minecraft server\plugins\HyroCosmetics-1.1.0.jar"
    echo Deployed successfully to server!
) else if exist "c:\Minecraft server\plugins" (
    echo.
    echo Deploying to local Minecraft server: c:\Minecraft server\plugins ...
    copy /y "target\HyroCosmetics-1.1.0.jar" "c:\Minecraft server\plugins\HyroCosmetics-1.1.0.jar"
    echo Deployed successfully to server!
)

pause

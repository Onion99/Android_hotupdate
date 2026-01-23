@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ======================================
echo Maven Central Publishing Tool
echo ======================================
echo.

REM Read credentials from gradle.properties
for /f "tokens=1,2 delims==" %%a in (..\gradle.properties) do (
    if "%%a"=="ossrhUsername" set USERNAME=%%b
    if "%%a"=="ossrhPassword" set PASSWORD=%%b
)

if "%USERNAME%"=="" (
    echo [ERROR] ossrhUsername not found
    pause
    exit /b 1
)

if "%PASSWORD%"=="" (
    echo [ERROR] ossrhPassword not found
    pause
    exit /b 1
)

REM Read version from maven-publish.gradle
for /f "tokens=1,2 delims==" %%a in ('findstr /C:"pomVersion = " ..\maven-publish.gradle') do (
    set VERSION_LINE=%%b
)
REM Remove quotes and spaces
set VERSION=%VERSION_LINE:'=%
set VERSION=%VERSION: =%
set VERSION=%VERSION:"=%

if "%VERSION%"=="" (
    echo [ERROR] Version not found in maven-publish.gradle
    pause
    exit /b 1
)

echo Detected version: %VERSION%
echo.

REM Generate Base64 encoded auth token
powershell -Command "$auth = '%USERNAME%:%PASSWORD%'; $bytes = [System.Text.Encoding]::UTF8.GetBytes($auth); $base64 = [Convert]::ToBase64String($bytes); Write-Host $base64" > temp_token.txt
set /p AUTH_TOKEN=<temp_token.txt
del temp_token.txt

echo Choose operation:
echo.
echo 1. Quick Publish (Recommended) - Build 5 modules and upload
echo 2. Full Publish - Clean, build 5 modules, and upload
echo 3. Publish patch-gradle-plugin to Maven Central
echo 4. Check deployment status
echo 5. Check Maven Central sync status
echo 6. Clear all deployments
echo.
set /p CHOICE="Enter option (1-6): "

if "%CHOICE%"=="1" goto QUICK_PUBLISH
if "%CHOICE%"=="2" goto FULL_PUBLISH
if "%CHOICE%"=="3" goto PUBLISH_PLUGIN
if "%CHOICE%"=="4" goto CHECK_STATUS
if "%CHOICE%"=="5" goto CHECK_MAVEN
if "%CHOICE%"=="6" goto CLEAR_DEPLOYMENTS

echo Invalid option
pause
exit /b 1

:QUICK_PUBLISH
echo.
echo ========================================
echo Quick Publish (5 modules)
echo ========================================
echo.
echo Publishing modules:
echo   - patch-core (Core patch library)
echo   - patch-native (Native C++ engine)
echo   - patch-generator-android (Android patch generator)
echo   - patch-cli (Command-line tool)
echo   - update (Hot update core library)
echo.
echo [1/3] Publishing to local repository...
cd ..
call gradlew.bat :patch-core:publishMavenPublicationToLocalRepository :patch-native:publishMavenPublicationToLocalRepository :patch-generator-android:publishMavenPublicationToLocalRepository :patch-cli:publishMavenPublicationToLocalRepository :update:publishMavenPublicationToLocalRepository
if errorlevel 1 (
    echo [ERROR] Publish failed
    pause
    exit /b 1
)

echo.
echo [2/3] Creating Bundle...
cd patch-core\build\repo
if not exist "..\..\..\temp_bundle_build" mkdir "..\..\..\temp_bundle_build"
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd patch-native\build\repo
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd patch-generator-android\build\repo
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd patch-cli\build\repo
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd update\build\repo
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd temp_bundle_build
powershell -Command "Compress-Archive -Path io -DestinationPath ..\maven-central\bundle.zip -Force"
cd ..
rmdir /s /q temp_bundle_build

if not exist "maven-central\bundle.zip" (
    echo [ERROR] Bundle creation failed
    pause
    exit /b 1
)

echo [SUCCESS] Bundle created
powershell -Command "Get-Item maven-central\bundle.zip | Select-Object Name, @{Name='Size(KB)';Expression={[math]::Round($_.Length/1KB,2)}}"

echo.
echo [3/3] Uploading to Central Portal...
cd maven-central
curl -X POST ^
     -H "Authorization: Bearer %AUTH_TOKEN%" ^
     -F "bundle=@bundle.zip" ^
     "https://central.sonatype.com/api/v1/publisher/upload?name=android-hotupdate-%VERSION%&publishingType=USER_MANAGED" ^
     > deployment_response.json

echo.
echo [SUCCESS] Upload complete!
echo.
echo Deployment ID:
type deployment_response.json
echo.
echo.
echo Published modules:
echo   - patch-core:%VERSION%
echo   - patch-native:%VERSION% (AAR with native SO libraries)
echo   - patch-generator-android:%VERSION%
echo   - patch-cli:%VERSION% (with -all classifier for fat JAR)
echo   - update:%VERSION%
echo.
echo Next steps:
echo   1. Visit https://central.sonatype.com/publishing/deployments
echo   2. Wait for validation (about 2-5 minutes)
echo   3. Click "Publish" to release
echo.
cd ..
pause
exit /b 0

:FULL_PUBLISH
echo.
echo ========================================
echo Full Publish (5 modules)
echo ========================================
echo.
echo Publishing modules:
echo   - patch-core (Core patch library)
echo   - patch-native (Native C++ engine)
echo   - patch-generator-android (Android patch generator)
echo   - patch-cli (Command-line tool)
echo   - update (Hot update core library)
echo.
echo [1/5] Cleaning build...
cd ..
call gradlew.bat clean

echo.
echo [2/5] Building project...
call gradlew.bat build -x test

echo.
echo [3/5] Publishing to local repository...
call gradlew.bat :patch-core:publishMavenPublicationToLocalRepository :patch-native:publishMavenPublicationToLocalRepository :patch-generator-android:publishMavenPublicationToLocalRepository :patch-cli:publishMavenPublicationToLocalRepository :update:publishMavenPublicationToLocalRepository
if errorlevel 1 (
    echo [ERROR] Build failed
    pause
    exit /b 1
)

echo.
echo [4/5] Creating Bundle...
cd patch-core\build\repo
if not exist "..\..\..\temp_bundle_build" mkdir "..\..\..\temp_bundle_build"
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd patch-native\build\repo
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd patch-generator-android\build\repo
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd patch-cli\build\repo
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd update\build\repo
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd temp_bundle_build
powershell -Command "Compress-Archive -Path io -DestinationPath ..\maven-central\bundle.zip -Force"
cd ..
rmdir /s /q temp_bundle_build

if not exist "maven-central\bundle.zip" (
    echo [ERROR] Bundle creation failed
    pause
    exit /b 1
)

echo [SUCCESS] Bundle created
powershell -Command "Get-Item maven-central\bundle.zip | Select-Object Name, @{Name='Size(KB)';Expression={[math]::Round($_.Length/1KB,2)}}"

echo.
echo [5/5] Uploading to Central Portal...
cd maven-central
curl -X POST ^
     -H "Authorization: Bearer %AUTH_TOKEN%" ^
     -F "bundle=@bundle.zip" ^
     "https://central.sonatype.com/api/v1/publisher/upload?name=android-hotupdate-%VERSION%&publishingType=USER_MANAGED" ^
     > deployment_response.json

echo.
echo [SUCCESS] Upload complete!
echo.
echo Deployment ID:
type deployment_response.json
echo.
echo.
echo Published modules:
echo   - patch-core:%VERSION%
echo   - patch-native:%VERSION% (AAR with native SO libraries)
echo   - patch-generator-android:%VERSION%
echo   - patch-cli:%VERSION% (with -all classifier for fat JAR)
echo   - update:%VERSION%
echo.
echo Next steps:
echo   1. Visit https://central.sonatype.com/publishing/deployments
echo   2. Wait for validation (about 2-5 minutes)
echo   3. Click "Publish" to release
echo.
cd ..
pause
exit /b 0

:PUBLISH_PLUGIN
echo.
echo ========================================
echo Publish patch-gradle-plugin to Maven Central
echo ========================================
echo.
echo This will publish patch-gradle-plugin to Maven Central only.
echo To publish to Gradle Plugin Portal, use: publish-plugin.bat
echo.
set /p CONFIRM="Continue? (Y/N): "
if /i not "%CONFIRM%"=="Y" (
    echo Cancelled
    pause
    exit /b 0
)

echo.
echo Publishing patch-gradle-plugin...
cd ..
call gradlew.bat :patch-gradle-plugin:clean :patch-gradle-plugin:publishPluginMavenPublicationToSonatypeRepository
if errorlevel 1 (
    echo [ERROR] Publish failed
    pause
    exit /b 1
)

echo.
echo [SUCCESS] patch-gradle-plugin published to Maven Central!
echo.
echo Next steps:
echo   1. Visit https://central.sonatype.com/publishing/deployments
echo   2. Wait for validation (about 2-5 minutes)
echo   3. Click "Publish" to release
echo.
echo To also publish to Gradle Plugin Portal, run: publish-plugin.bat
echo.
cd maven-central
pause
exit /b 0

:CHECK_STATUS
echo.
set /p DEPLOYMENT_ID="Enter deployment ID: "
if "%DEPLOYMENT_ID%"=="" (
    echo [ERROR] Deployment ID cannot be empty
    pause
    exit /b 1
)

echo.
echo Checking deployment status...
cd maven-central
curl -X GET ^
     -H "Authorization: Bearer %AUTH_TOKEN%" ^
     "https://central.sonatype.com/api/v1/publisher/status?id=%DEPLOYMENT_ID%" ^
     > status_response.json

echo.
type status_response.json
echo.
cd ..
pause
exit /b 0

:CHECK_MAVEN
echo.
echo Checking Maven Central...
curl -s -o nul -w "%%{http_code}" "https://repo1.maven.org/maven2/io/github/706412584/patch-core/%VERSION%/patch-core-%VERSION%.pom" > temp_status.txt
set /p STATUS=<temp_status.txt
del temp_status.txt

if "%STATUS%"=="200" (
    echo [SUCCESS] Library is visible on Maven Central!
    echo.
    echo Usage:
    echo   implementation 'io.github.706412584:patch-core:%VERSION%'
    echo.
    echo View: https://repo1.maven.org/maven2/io/github/706412584/patch-core/%VERSION%/
) else (
    echo [PENDING] Still syncing (usually takes 15-30 minutes)
    echo HTTP status code: %STATUS%
)
echo.
pause
exit /b 0

:CLEAR_DEPLOYMENTS
echo.
echo [WARNING] This will delete all unpublished deployments
set /p CONFIRM="Confirm deletion? (Y/N): "
if /i not "%CONFIRM%"=="Y" (
    echo Cancelled
    pause
    exit /b 0
)

echo.
echo Fetching deployment list...
cd maven-central
curl -s -X GET ^
     -H "Authorization: Bearer %AUTH_TOKEN%" ^
     "https://central.sonatype.com/api/v1/publisher/deployments" ^
     > deployments_list.json

echo Deleting...
powershell -Command ^
    "$json = Get-Content deployments_list.json -Raw | ConvertFrom-Json; " ^
    "$authToken = '%AUTH_TOKEN%'; " ^
    "$count = 0; " ^
    "foreach ($deployment in $json) { " ^
    "    $id = $deployment.deploymentId; " ^
    "    $name = $deployment.name; " ^
    "    Write-Host \"Deleting: $name ($id)\"; " ^
    "    $headers = @{'Authorization' = \"Bearer $authToken\"}; " ^
    "    try { " ^
    "        Invoke-RestMethod -Uri \"https://central.sonatype.com/api/v1/publisher/deployment/$id\" -Method Delete -Headers $headers | Out-Null; " ^
    "        $count++; " ^
    "    } catch { } " ^
    "} " ^
    "Write-Host \"`n[SUCCESS] Deleted $count deployments\""

cd ..
echo.
pause
exit /b 0

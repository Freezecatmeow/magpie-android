@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-25"
set "SDK=E:\deepseek work\magpie-android\sdk"
call "%SDK%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%SDK%" --licenses
call "%SDK%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%SDK%" "platforms;android-34" "build-tools;34.0.0" "platform-tools"
echo SDKMAN_EXIT=%ERRORLEVEL%

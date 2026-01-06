set ANDROID_HOME=C:\Android\sdk
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr

rd /s /q %USERPROFILE%\.gradle\caches

gradlew --stop

gradlew clean
gradlew assembleDebug
pause
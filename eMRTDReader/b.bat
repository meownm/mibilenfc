set ANDROID_HOME=C:\Android\sdk
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr

gradlew --stop

rd /s /q %USERPROFILE%\.gradle\caches

gradlew clean
gradlew assembleDebug
pause
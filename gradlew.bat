@echo off
setlocal

set SCRIPT_DIR=%~dp0
set WRAPPER_DIR=%SCRIPT_DIR%gradle\wrapper
set CLASSPATH=%WRAPPER_DIR%\gradle-wrapper.jar;%WRAPPER_DIR%\gradle-wrapper-shared.jar;%WRAPPER_DIR%\gradle-cli.jar;%WRAPPER_DIR%\gradle-functional.jar;%WRAPPER_DIR%\gradle-files.jar;%WRAPPER_DIR%\gradle-base-annotations.jar

if defined JAVA_HOME (
    set JAVA_EXEC=%JAVA_HOME%\bin\java.exe
) else (
    set JAVA_EXEC=java
)

"%JAVA_EXEC%" -Xmx64m -Xms64m -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

endlocal

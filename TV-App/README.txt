TVBox 电视端（Android）
======================

请用 Android Studio 打开本目录（TV-App），不要打开上一级仓库根目录。

构建：
  .\gradlew.bat assembleJava64Release

或通过仓库根目录脚本：
  ..\scripts\build.ps1

云遥控相关代码：
  app\src\main\java\com\github\tvbox\osc\cloud\CloudRemoteClient.java
  app\src\main\java\com\github\tvbox\osc\server\RemoteSettingsHelper.java

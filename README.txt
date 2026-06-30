TVBox 仓库结构
================

TV-App/           电视端（Android Studio 打开此目录）
  app/              TVBox 主模块
  player/           播放器模块
  quickjs/          QuickJS 模块
  pyramid/          Python 爬虫模块
  gradlew.bat       构建入口

remote-control/   遥控端（手机网页 + Node 后端 :3080）
  backend/          npm start
  frontend/         静态页面

scripts/          构建、安装、配置测试脚本
self-host/        本地配置 HTTP 服务

常用命令（在仓库根目录）：
  .\scripts\open-android-studio.ps1
  .\scripts\build.ps1
  .\scripts\install.ps1
  cd remote-control\backend && npm start

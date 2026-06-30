本目录存放固定输出的 APK（由 scripts\copy-release.ps1 或 build.ps1 生成）。

结构:
  release/apk/<flavor>/<type>/TVBox_<type>-<flavor>.apk
  release/apk/manifest.json   — 文件清单

安装:
  .\scripts\install.ps1 -Flavor java64 -Type release

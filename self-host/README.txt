自建 TVBox 接口说明
==================

推荐配置仓: https://github.com/zhlong123/tvboxConfig

一、最快方式（无需本地服务）
----------------------------
TVBox 设置 → 配置地址，填入以下任一 URL：

  多仓索引:
    https://raw.githubusercontent.com/zhlong123/tvboxConfig/master/0707.json

  家庭电视单线（推荐，含 jar/js/py 相对路径）:
    https://raw.githubusercontent.com/zhlong123/tvboxConfig/master/jsm.json

含 Python 源时请安装 python64 变体 APK。

二、局域网本地服务（模拟器/电视与 PC 同网）
------------------------------------------
1. 同步配置仓:
     .\self-host\sync-tvbox-config.ps1

2. 启动服务:
     .\self-host\start-server.ps1

3. TVBox 配置地址（把 127.0.0.1 换成本机 IP，模拟器不要用 127.0.0.1）:
     http://<本机IP>:9978/config/index.json

   或直接单线:
     http://<本机IP>:9978/tvboxConfig/jsm.json

本机 IP: cmd 执行 ipconfig，查看 IPv4 地址。

三、自定义 CMS 模板
------------------
编辑 config/main.json，将「你的域名」「你的解析地址」改为真实地址后，
通过 index.json 中「自定义 CMS 模板」线路加载。

注意: 请勿托管未经授权的影视资源；配置仅供学习测试，请自行判断合法性。

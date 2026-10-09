# 高德地图配置与验收

## 当前位置定位规则（2026年10月9日更新）

“使用当前位置”直接调用浏览器 navigator.geolocation.getCurrentPosition，设置高精度、8 秒超时、不使用缓存。非安全环境直接提示 HTTPS；电脑 localhost 可测试，手机局域网 HTTP 不能用于精确定位。是否弹出权限框由浏览器现有权限状态决定，不保证每次弹窗。

只接受浏览器报告的有限、正数且不超过 100 米的 accuracy，再通过 AMap.convertFrom(..., 'gps', ...) 将坐标转换为 GCJ02。精度门槛是浏览器报告值筛选，不保证真实误差绝对小于 100 米，也不能保证浏览器底层采用 GPS。应用不调用高德 Geolocation，不做 IP/城市回退；失败保留原选点并提示地图选点，不直接保存原始 GPS 坐标。关闭或更换选点后旧回调无效。

用户可搜索地点、点击地图、拖动标记或主动定位，确认后才更换搜索起点；取消不改变原值。列表和地图共用查询结果，最多展示 100 个空间，距离为直线距离。数据管理员可地图选点，或明确确认手工输入的 GCJ02 坐标。

## 本地配置

在高德开放平台创建 Web 端（JS API）应用，配置实际访问域名，取得 Key 和安全密钥。在 `backend` 目录的后端启动终端执行：

```powershell
$env:CF_AMAP_JS_KEY = Read-Host '高德 Web 端 JS API Key'
$amapSecret = Read-Host '高德安全密钥' -AsSecureString
$env:CF_AMAP_SECURITY_JS_CODE = [System.Net.NetworkCredential]::new('', $amapSecret).Password
Remove-Variable amapSecret
$env:CF_PORT = '18080'
java -jar target/campusflow-0.1.0.jar --spring.profiles.active=local
```

修改环境变量后重启后端，前端无需配置安全密钥。不要将真实凭证写入源码或提交 Git。Key 提供给浏览器加载 SDK，安全密钥仅由后端代理注入。正式部署让前端与 `/api` 同源，并按实际域名配置应用。

未配置凭证或 SDK 加载失败时显示原因，可继续使用列表、预置点和手动坐标。定位仅在点击后执行，不持续跟踪；手机定位需 HTTPS 与浏览器位置授权，失败时可搜索或手动选点。

## 接口与坐标约定

- `GET /api/amap/config`：登录后返回 `{configured, key}`；缺任一配置时返回 `false` 和空 Key，不返回安全密钥。
- `GET /_AMapService/**`：登录后仅代理固定 HTTPS 上游的地点搜索、逆地理编码、坐标转换、IP 定位、地图样式和矢量图路径。禁止重定向，不转发 Cookie，替换请求中的 Key 和安全密钥；未配置返回 503，非允许路径返回 400。
- 坐标统一 GCJ02，不将浏览器原始 GPS 坐标直接用于距离。地址和精度可缺省，定位精度用于地图提示。
- V6 增加 `study_space.coordinate_system`。仅原始十条演示记录的名称和经纬度完全匹配时标记 GCJ02；其他旧坐标为 UNKNOWN，不猜测转换、不进入推荐。管理员须选点或明确确认手工 GCJ02 坐标后保存，其他字段编辑不能自动确认未知坐标。
- 演示坐标为合成数据，不代表真实校园。

## 联调范围

自动测试覆盖缺配置、代理鉴权与路径限制、坐标校验、旧回调隔离和起点切换竞争。当前未配置真实凭证，在线地图、代理与 SDK 的真实兼容性及手机实机定位仍待验证。

配置后检查地图加载、搜索、点击拖动、定位成功与拒绝、确认取消、管理员保存后的用户地图标记、切换起点后的距离与详情一致；在手机 HTTPS 页面检查权限与精度提示，并确认浏览器响应中没有安全密钥。

官方参考：[安全密钥代理](https://developer.amap.com/api/javascript-api-v2/guide/abc/jscode)、[坐标转换](https://developer.amap.com/api/javascript-api-v2/guide/transform/convertfrom)、[浏览器定位](https://developer.amap.com/api/javascript-api-v2/guide/services/geolocation)。

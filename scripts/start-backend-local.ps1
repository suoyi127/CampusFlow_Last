$ErrorActionPreference = 'Stop'
$backendDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) 'backend'
$credentialFile = Join-Path $backendDirectory 'data/checkin-local.properties'
if (-not (Test-Path -LiteralPath $credentialFile)) {
    throw '缺少 backend/data/checkin-local.properties 本地 MQTT 凭据配置。'
}
Push-Location $backendDirectory
try {
    # 本地凭据不进入源码；设备绑定、broker 地址和开关仍使用原环境变量。
    & java.exe -jar target/campusflow-0.1.0.jar --spring.profiles.active=local --server.port=18080 --spring.config.additional-location=file:./data/checkin-local.properties
    if ($LASTEXITCODE -ne 0) { throw '后端启动失败，请查看上述日志。' }
} finally { Pop-Location }

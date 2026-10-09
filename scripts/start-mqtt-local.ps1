$ErrorActionPreference = 'Stop'
$brokerExecutable = Join-Path $env:ProgramFiles 'mosquitto/mosquitto.exe'
$brokerDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) 'backend/data/checkin-broker'
$brokerConfiguration = Join-Path $brokerDirectory 'mosquitto.conf'
if (-not (Test-Path -LiteralPath $brokerExecutable)) { throw '未找到 Mosquitto 可执行文件。' }
if (-not (Test-Path -LiteralPath $brokerConfiguration)) { throw '缺少项目本地 broker 配置。' }
# 使用项目自己的账号文件，不修改已安装服务的全局配置。
& $brokerExecutable -c $brokerConfiguration -v
if ($LASTEXITCODE -ne 0) { throw 'MQTT 启动失败；检查日志，并确认 1883 未被另一个 broker 占用。' }

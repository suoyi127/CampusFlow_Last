$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$backendDirectory = Join-Path $projectDirectory 'backend'
# Windows 用户临时目录可能无法建立 Java 内部套接字；独立目录也避免被 Maven clean 删除。
$socketDirectory = Join-Path $projectDirectory '.cache/java-sockets'
New-Item -ItemType Directory -Path $socketDirectory -Force | Out-Null
Push-Location $backendDirectory
try {
    & mvn.cmd clean verify "-DargLine=-Djdk.net.unixdomain.tmpdir=$socketDirectory"
    if ($LASTEXITCODE -ne 0) { throw '后端测试或打包失败，请查看 Maven 输出。' }
} finally {
    Pop-Location
}

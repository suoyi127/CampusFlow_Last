$ErrorActionPreference = 'Stop'
$backendDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) 'backend'
$databaseConfiguration = Join-Path $backendDirectory 'data/mysql-local.properties'
if (-not (Test-Path -LiteralPath $databaseConfiguration)) {
    throw '请先运行 scripts/initialize-real-database.ps1，创建真实 MySQL 数据库与本机配置。'
}
$previousUsername = $env:CF_ADMIN_USERNAME
$previousPassword = $env:CF_ADMIN_PASSWORD
$secretPointer = [IntPtr]::Zero
Push-Location $backendDirectory
try {
    $bootstrap = Read-Host '首次启动需要创建服务器管理员？输入 y；已有真实管理员直接回车'
    if ($bootstrap -eq 'y') {
        $env:CF_ADMIN_USERNAME = Read-Host '新的服务器管理员账号（3—64 位字母、数字、下划线）'
        $adminSecret = Read-Host '新管理员密码（至少8字符，最多72个UTF-8字节）' -AsSecureString
        $secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminSecret)
        $env:CF_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
    }
    & java.exe -jar target/campusflow-0.1.0.jar --spring.profiles.active=local --server.port=8080 --spring.config.additional-location=file:./data/mysql-local.properties
    if ($LASTEXITCODE -ne 0) { throw '后端启动失败，请查看上述日志。' }
} finally {
    $env:CF_ADMIN_USERNAME = $previousUsername
    $env:CF_ADMIN_PASSWORD = $previousPassword
    if ($secretPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer) }
    Pop-Location
}

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$dataDirectory = Join-Path $projectDirectory 'backend/data'
$configuration = Join-Path $dataDirectory 'mysql-local.properties'
if (Test-Path -LiteralPath $configuration) {
    throw '本机真实数据库配置已存在。为避免重置已有数据库账号，本脚本停止。'
}
$mysql = (Get-Command mysql.exe -ErrorAction Stop).Source
$adminUser = Read-Host 'MySQL 管理员用户名（直接回车使用 root）'
if ([string]::IsNullOrWhiteSpace($adminUser)) { $adminUser = 'root' }
$adminSecret = Read-Host 'MySQL 管理员密码' -AsSecureString
$secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminSecret)
$previousPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
    # 应用密码由本机随机生成，不使用默认口令，也不出现在命令参数与日志中。
    $randomBytes = New-Object byte[] 32
    $randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $randomGenerator.GetBytes($randomBytes) } finally { $randomGenerator.Dispose() }
    $appPassword = [BitConverter]::ToString($randomBytes).Replace('-', '')
    $existing = & $mysql --host=127.0.0.1 --user=$adminUser --batch --skip-column-names --execute="SELECT COUNT(*) FROM mysql.user WHERE User='campusflow_app' AND Host='localhost';"
    if ($LASTEXITCODE -ne 0) { throw 'MySQL 管理员认证失败；未创建数据库配置。' }
    if ($existing.Trim() -ne '0') { throw 'campusflow_app 账号已存在；为避免覆盖口令，请配置其现有凭据。' }
    $sql = @"
CREATE DATABASE IF NOT EXISTS campusflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'campusflow_app'@'localhost' IDENTIFIED BY '$appPassword';
GRANT ALL PRIVILEGES ON campusflow.* TO 'campusflow_app'@'localhost';
"@
    $sql | & $mysql --host=127.0.0.1 --user=$adminUser --batch
    if ($LASTEXITCODE -ne 0) { throw 'MySQL 初始化失败，请检查权限；未写入配置。' }
    New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
    $content = @"
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/campusflow?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
spring.datasource.username=campusflow_app
spring.datasource.password=$appPassword
"@
    [IO.File]::WriteAllText($configuration, $content, [Text.UTF8Encoding]::new($false))
    Write-Host '真实数据库与专用账号已创建，本机连接配置已保存；未插入业务样例。'
} finally {
    $env:MYSQL_PWD = $previousPassword
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)
    $appPassword = $null
}

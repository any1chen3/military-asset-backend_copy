@echo off
REM 设置控制台为UTF-8编码
REM 有问题可以先执行 mvn clean install
chcp 65001 > nul

echo ================================
echo   资产管理系统后端启动程序
echo   Asset Management System
echo ================================
echo.

echo 正在启动后端服务...
cd /d "D:\Projects\Asset Management Project\military-asset-backend"
mvn spring-boot:run
echo.
echo After startup, visit: http://localhost:8080/api/asset/
echo Press Ctrl+C to stop the service
pause
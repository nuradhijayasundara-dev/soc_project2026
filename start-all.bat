@echo off
title Backhaul-Match Launcher
echo ============================================
echo   Backhaul-Match - Starting All Services
echo ============================================
echo.

set ROOT=%~dp0

echo [1/14] Starting Discovery Server (port 8761)...
start "Discovery Server" cmd /k "cd /d %ROOT%backend\discovery-server && mvn spring-boot:run"
echo        Waiting for Eureka to be ready...
timeout /t 30 /nobreak >nul

echo [2/14] Starting Auth Service (port 8081)...
start "Auth Service" cmd /k "cd /d %ROOT%backend\auth-service && mvn spring-boot:run"
timeout /t 10 /nobreak >nul

echo [3/14] Starting User Service (port 8082)...
start "User Service" cmd /k "cd /d %ROOT%backend\user-service && mvn spring-boot:run"
timeout /t 8 /nobreak >nul

echo [4/14] Starting Courier Service (port 8083)...
start "Courier Service" cmd /k "cd /d %ROOT%backend\courier-service && mvn spring-boot:run"
timeout /t 8 /nobreak >nul

echo [5/14] Starting Fleet Service (port 8084)...
start "Fleet Service" cmd /k "cd /d %ROOT%backend\fleet-service && mvn spring-boot:run"
timeout /t 8 /nobreak >nul

echo [6/14] Starting GPS Service (port 8085)...
start "GPS Service" cmd /k "cd /d %ROOT%backend\gps-service && mvn spring-boot:run"
timeout /t 8 /nobreak >nul

echo [7/14] Starting Matching Service (port 8086)...
start "Matching Service" cmd /k "cd /d %ROOT%backend\matching-service && mvn spring-boot:run"
timeout /t 8 /nobreak >nul

echo [8/14] Starting Notification Service (port 8087)...
start "Notification Service" cmd /k "cd /d %ROOT%backend\notification-service && mvn spring-boot:run"
timeout /t 8 /nobreak >nul

echo [9/14] Starting Payment Service (port 8088)...
start "Payment Service" cmd /k "cd /d %ROOT%backend\payment-service && mvn spring-boot:run"
timeout /t 8 /nobreak >nul

echo [10/14] Starting Admin Service (port 8089)...
start "Admin Service" cmd /k "cd /d %ROOT%backend\admin-service && mvn spring-boot:run"
timeout /t 8 /nobreak >nul

echo [11/14] Starting API Gateway (port 8080)...
start "API Gateway" cmd /k "cd /d %ROOT%backend\api-gateway && mvn spring-boot:run"
timeout /t 15 /nobreak >nul

echo.
echo --- All backends launched! Starting frontends ---
echo.

echo [12/14] Installing Courier Portal dependencies...
cd /d %ROOT%frontend\courier-portal && call npm install --silent
echo [12/14] Starting Courier Portal (port 3000)...
start "Courier Portal" cmd /k "cd /d %ROOT%frontend\courier-portal && npm start"

echo [13/14] Installing Fleet Portal dependencies...
cd /d %ROOT%frontend\fleet-portal && call npm install --silent
echo [13/14] Starting Fleet Portal (port 3001)...
start "Fleet Portal" cmd /k "cd /d %ROOT%frontend\fleet-portal && set PORT=3001 && npm start"

echo [14/14] Installing Admin Portal dependencies...
cd /d %ROOT%frontend\admin-portal && call npm install --silent
echo [14/14] Starting Admin Portal (port 3002)...
start "Admin Portal" cmd /k "cd /d %ROOT%frontend\admin-portal && set PORT=3002 && npm start"

echo.
echo ============================================
echo   All services launched!
echo ============================================
echo.
echo   BACKENDS:
echo     Discovery Server  : http://localhost:8761
echo     API Gateway       : http://localhost:8080
echo     Auth Service      : http://localhost:8081
echo     User Service      : http://localhost:8082
echo     Courier Service   : http://localhost:8083
echo     Fleet Service     : http://localhost:8084
echo     GPS Service       : http://localhost:8085
echo     Matching Service  : http://localhost:8086
echo     Notification Svc  : http://localhost:8087
echo     Payment Service   : http://localhost:8088
echo     Admin Service     : http://localhost:8089
echo.
echo   FRONTENDS:
echo     Courier Portal    : http://localhost:3000
echo     Fleet Portal      : http://localhost:3001
echo     Admin Portal      : http://localhost:3002
echo.
echo   Each service runs in its own terminal window.
echo   Close a window to stop that service.
echo ============================================
pause

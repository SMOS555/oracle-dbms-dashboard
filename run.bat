@echo off
echo ==========================================
echo Oracle DBMS Dashboard
echo ==========================================
if "%DB_URL%"=="" set /p DB_URL=Oracle JDBC URL [jdbc:oracle:thin:@localhost:1521/XEPDB1]:
if "%DB_USERNAME%"=="" set /p DB_USERNAME=Oracle username:
if "%DB_PASSWORD%"=="" set /p DB_PASSWORD=Oracle password:
echo Starting on http://localhost:8080
mvn spring-boot:run
pause

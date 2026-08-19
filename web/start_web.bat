@echo off
echo Starting AGV Web Dashboard...
echo ===================================

echo [1/2] Starting Node.js Backend (Port 3001)...
cd backend
start cmd /k "npm install && node server.js"

cd ..
echo [2/2] Starting React Frontend (Port 3000)...
cd frontend
start cmd /k "npm install && npm run dev"

echo ===================================
echo Both servers are starting up in separate windows!
echo Please wait a few seconds and then open your browser at:
echo http://localhost:3000
echo ===================================
pause

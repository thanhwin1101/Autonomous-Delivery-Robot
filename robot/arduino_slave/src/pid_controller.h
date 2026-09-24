#pragma once
// ====================================================================
//  Closed-Loop PID Controller with Anti-Windup (Embedded C++ OOP)
// ====================================================================
#include <Arduino.h>

class ClosedLoopPid {
private:
    float _kp, _ki, _kd;
    float _integral;
    float _lastError;
    float _integralLimit;

public:
    ClosedLoopPid(float kp = 3.5f, float ki = 0.8f, float kd = 0.2f, float iLimit = 200.0f)
        : _kp(kp), _ki(ki), _kd(kd), _integral(0.0f), _lastError(0.0f), _integralLimit(iLimit) {}

    float compute(float target, float current) {
        float error = target - current;

        // Tích phân có chống bão hòa (Anti-windup clamping)
        _integral = constrain(_integral + error, -_integralLimit, _integralLimit);

        float derivative = error - _lastError;
        _lastError = error;

        float output = (_kp * error) + (_ki * _integral) + (_kd * derivative);
        return output;
    }

    void reset() {
        _integral = 0.0f;
        _lastError = 0.0f;
    }

    void setGains(float kp, float ki, float kd) {
        _kp = kp;
        _ki = ki;
        _kd = kd;
    }
};

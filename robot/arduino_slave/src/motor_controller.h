#pragma once
// ====================================================================
//  L298N Dual Motor Controller (Embedded C++ OOP)
// --------------------------------------------------------------------
//  - Quản lý 2 kênh động cơ DC qua cầu H L298N
//  - Dùng Hardware LEDC PWM của ESP32 (5kHz, 8-bit)
//  - Cung cấp API setSpeed(), emergencyStop(), brake()
// ====================================================================
#include <Arduino.h>

class L298nDualMotor {
private:
    uint8_t _enaPin, _in1Pin, _in2Pin;
    uint8_t _enbPin, _in3Pin, _in4Pin;
    uint8_t _pwmChannelA;
    uint8_t _pwmChannelB;

public:
    L298nDualMotor(uint8_t ena, uint8_t in1, uint8_t in2, uint8_t chA,
                   uint8_t enb, uint8_t in3, uint8_t in4, uint8_t chB)
        : _enaPin(ena), _in1Pin(in1), _in2Pin(in2), _pwmChannelA(chA),
          _enbPin(enb), _in3Pin(in3), _in4Pin(in4), _pwmChannelB(chB) {}

    void init(uint32_t freq = 5000, uint8_t resolution = 8) {
        pinMode(_in1Pin, OUTPUT);
        pinMode(_in2Pin, OUTPUT);
        pinMode(_in3Pin, OUTPUT);
        pinMode(_in4Pin, OUTPUT);

        ledcSetup(_pwmChannelA, freq, resolution);
        ledcSetup(_pwmChannelB, freq, resolution);
        ledcAttachPin(_enaPin, _pwmChannelA);
        ledcAttachPin(_enbPin, _pwmChannelB);

        stop();
    }

    void setSpeed(int16_t pwmL, int16_t pwmR) {
        // Động cơ trái
        if (pwmL > 255) pwmL = 255;
        if (pwmL < -255) pwmL = -255;
        if (pwmL > 0) {
            digitalWrite(_in1Pin, HIGH);
            digitalWrite(_in2Pin, LOW);
            ledcWrite(_pwmChannelA, pwmL);
        } else if (pwmL < 0) {
            digitalWrite(_in1Pin, LOW);
            digitalWrite(_in2Pin, HIGH);
            ledcWrite(_pwmChannelA, -pwmL);
        } else {
            digitalWrite(_in1Pin, LOW);
            digitalWrite(_in2Pin, LOW);
            ledcWrite(_pwmChannelA, 0);
        }

        // Động cơ phải
        if (pwmR > 255) pwmR = 255;
        if (pwmR < -255) pwmR = -255;
        if (pwmR > 0) {
            digitalWrite(_in3Pin, HIGH);
            digitalWrite(_in4Pin, LOW);
            ledcWrite(_pwmChannelB, pwmR);
        } else if (pwmR < 0) {
            digitalWrite(_in3Pin, LOW);
            digitalWrite(_in4Pin, HIGH);
            ledcWrite(_pwmChannelB, -pwmR);
        } else {
            digitalWrite(_in3Pin, LOW);
            digitalWrite(_in4Pin, LOW);
            ledcWrite(_pwmChannelB, 0);
        }
    }

    void stop() {
        digitalWrite(_in1Pin, LOW);
        digitalWrite(_in2Pin, LOW);
        digitalWrite(_in3Pin, LOW);
        digitalWrite(_in4Pin, LOW);
        ledcWrite(_pwmChannelA, 0);
        ledcWrite(_pwmChannelB, 0);
    }
};

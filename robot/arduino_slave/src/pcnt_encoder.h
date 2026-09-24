#pragma once
// ====================================================================
//  ESP32 Hardware Pulse Counter (PCNT) Driver – Zero CPU Overhead
// --------------------------------------------------------------------
//  - Sử dụng module phần cứng PCNT tích hợp trên SoC ESP32
//  - Đếm xung tự động hoàn toàn bằng phần cứng
//  - Tích hợp bộ lọc nhiễu phần cứng (Hardware Glitch Filter)
//  - Không tốn chu kỳ CPU, không gây trễ ngắt ở tốc độ cao
// ====================================================================
#include <Arduino.h>
#include "driver/pcnt.h"

class PcntEncoder {
private:
    pcnt_unit_t _unit;
    int _pulsePin;
    int16_t _lastCount;
    int32_t _accumulatedCount;

public:
    PcntEncoder(pcnt_unit_t unit, int pulsePin)
        : _unit(unit), _pulsePin(pulsePin), _lastCount(0), _accumulatedCount(0) {}

    void init() {
        pcnt_config_t pcnt_config = {
            .pulse_gpio_num = _pulsePin,
            .ctrl_gpio_num  = PCNT_PIN_NOT_USED,
            .lctrl_mode     = PCNT_MODE_KEEP,
            .hctrl_mode     = PCNT_MODE_KEEP,
            .pos_mode       = PCNT_COUNT_INC,  // Đếm sườn dương
            .neg_mode       = PCNT_COUNT_DIS,  // Bỏ qua sườn âm
            .counter_h_lim  = 30000,
            .counter_l_lim  = -30000,
            .unit           = _unit,
            .channel        = PCNT_CHANNEL_0,
        };

        pcnt_unit_config(&pcnt_config);

        // Bật bộ lọc phần cứng: lọc mọi xung nhiễu ngắn hơn 1000 chu kỳ APB clock (~12.5µs)
        pcnt_set_filter_value(_unit, 1000);
        pcnt_filter_enable(_unit);

        pcnt_counter_pause(_unit);
        pcnt_counter_clear(_unit);
        pcnt_counter_resume(_unit);

        _lastCount = 0;
        _accumulatedCount = 0;
    }

    // Đọc số xung tích lũy và cập nhật
    int32_t getPulseCount(bool forward = true) {
        int16_t currentHwCount = 0;
        pcnt_get_counter_value(_unit, &currentHwCount);

        int16_t delta = currentHwCount - _lastCount;
        _lastCount = currentHwCount;

        if (forward) {
            _accumulatedCount += delta;
        } else {
            _accumulatedCount -= delta;
        }

        // Tự động clear thanh ghi phần cứng khi gần ngưỡng để tránh tràn
        if (abs(currentHwCount) > 25000) {
            pcnt_counter_pause(_unit);
            pcnt_counter_clear(_unit);
            pcnt_counter_resume(_unit);
            _lastCount = 0;
        }

        return _accumulatedCount;
    }

    // Đọc số xung chênh lệch kể từ lần gọi trước (Delta)
    int32_t getDeltaAndReset() {
        int16_t currentHwCount = 0;
        pcnt_get_counter_value(_unit, &currentHwCount);

        int32_t delta = currentHwCount - _lastCount;
        _lastCount = currentHwCount;

        if (abs(currentHwCount) > 25000) {
            pcnt_counter_pause(_unit);
            pcnt_counter_clear(_unit);
            pcnt_counter_resume(_unit);
            _lastCount = 0;
        }

        return delta;
    }
};

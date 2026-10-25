#pragma once

#include <string>
#include <mutex>
#include <map>

namespace untrusted {
namespace vm {

struct GpsLocation {
    double latitude{37.7749};
    double longitude{-122.4194};
    double altitude{15.0};
    float speed{0.0f};
    float bearing{0.0f};
    bool valid{true};
};

struct BatteryInfo {
    int level{85};
    bool isCharging{true};
    int temperature{310}; // 31.0 C (tenths of degree)
    int voltage{4100};     // mV
};

struct SensorData {
    float accel[3]{0.0f, 9.8f, 0.0f};
    float gyro[3]{0.0f, 0.0f, 0.0f};
    float light{150.0f};
};

class HalBox {
public:
    static HalBox& getInstance();

    void setGpsLocation(int vmId, double lat, double lng, double alt);
    std::string generateNmea(int vmId);

    void setBattery(int vmId, int level, bool isCharging, int temp);
    std::string getBatterySysfs(int vmId, const std::string& node);

    void setSensors(int vmId, float ax, float ay, float az, float gx, float gy, float gz);
    SensorData getSensors(int vmId);

private:
    HalBox() = default;

    std::map<int, GpsLocation> gpsMap_;
    std::map<int, BatteryInfo> batteryMap_;
    std::map<int, SensorData> sensorMap_;
    std::mutex halMutex_;
};

} // namespace vm
} // namespace untrusted

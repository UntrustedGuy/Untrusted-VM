#include "vm_hal.h"
#include <cstdio>
#include <cmath>
#include <ctime>

namespace untrusted {
namespace vm {

HalBox& HalBox::getInstance() {
    static HalBox instance;
    return instance;
}

void HalBox::setGpsLocation(int vmId, double lat, double lng, double alt) {
    std::lock_guard<std::mutex> lock(halMutex_);
    GpsLocation& loc = gpsMap_[vmId];
    loc.latitude = lat;
    loc.longitude = lng;
    loc.altitude = alt;
    loc.valid = true;
}

std::string HalBox::generateNmea(int vmId) {
    std::lock_guard<std::mutex> lock(halMutex_);
    GpsLocation loc = gpsMap_[vmId];

    // Format standard NMEA $GPGGA sentence
    char buffer[128];
    char latHemi = loc.latitude >= 0 ? 'N' : 'S';
    char lngHemi = loc.longitude >= 0 ? 'E' : 'W';

    double absLat = std::abs(loc.latitude);
    double absLng = std::abs(loc.longitude);

    int latDeg = (int)absLat;
    double latMin = (absLat - latDeg) * 60.0;
    int lngDeg = (int)absLng;
    double lngMin = (absLng - lngDeg) * 60.0;

    snprintf(buffer, sizeof(buffer),
             "$GPGGA,120000.00,%02d%07.4f,%c,%03d%07.4f,%c,1,08,1.0,%.1f,M,0.0,M,,*47\r\n",
             latDeg, latMin, latHemi, lngDeg, lngMin, lngHemi, loc.altitude);

    return std::string(buffer);
}

void HalBox::setBattery(int vmId, int level, bool isCharging, int temp) {
    std::lock_guard<std::mutex> lock(halMutex_);
    BatteryInfo& b = batteryMap_[vmId];
    b.level = level;
    b.isCharging = isCharging;
    b.temperature = temp;
}

std::string HalBox::getBatterySysfs(int vmId, const std::string& node) {
    std::lock_guard<std::mutex> lock(halMutex_);
    BatteryInfo b = batteryMap_[vmId];

    if (node == "capacity") {
        return std::to_string(b.level) + "\n";
    } else if (node == "status") {
        return b.isCharging ? "Charging\n" : "Discharging\n";
    } else if (node == "temp") {
        return std::to_string(b.temperature) + "\n";
    } else if (node == "voltage_now") {
        return std::to_string(b.voltage * 1000) + "\n"; // uV
    }
    return "0\n";
}

void HalBox::setSensors(int vmId, float ax, float ay, float az, float gx, float gy, float gz) {
    std::lock_guard<std::mutex> lock(halMutex_);
    SensorData& s = sensorMap_[vmId];
    s.accel[0] = ax; s.accel[1] = ay; s.accel[2] = az;
    s.gyro[0] = gx;  s.gyro[1] = gy;  s.gyro[2] = gz;
}

SensorData HalBox::getSensors(int vmId) {
    std::lock_guard<std::mutex> lock(halMutex_);
    return sensorMap_[vmId];
}

} // namespace vm
} // namespace untrusted

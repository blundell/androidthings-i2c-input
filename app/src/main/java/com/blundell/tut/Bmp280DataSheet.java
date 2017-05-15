package com.blundell.tut;

class Bmp280DataSheet {

    /**
     * The BMP280 output consists of the Analog to Digital Converter output values.
     * However, each sensing element behaves differently,
     * and actual temperature must be calculated using a set of calibration parameters.
     * <p>
     * The calibration parameters are programmed into the devices’
     * non-volatile memory during production and cannot be altered.
     *
     * @param data            a2d output values
     * @param calibrationData constants from your specific peripherals mem, always size 3
     * @return temperature in degrees celsius
     */
    static float readTemperatureFrom(byte[] data, short[] calibrationData) {
        return compensateTemperature(readSample(data), calibrationData);
    }

    private static int readSample(byte[] data) {
        // msb[7:0] lsb[7:0] xlsb[7:4]
        int msb = data[0] & 0xff;
        int lsb = data[1] & 0xff;
        int xlsb = data[2] & 0xf0;
        // Convert to 20bit integer
        return (msb << 16 | lsb << 8 | xlsb) >> 4;
    }

    /**
     * Compensation formula from the BMP280 datasheet.
     * https://cdn-shop.adafruit.com/datasheets/BST-BMP280-DS001-11.pdf
     *
     * @param rawTemp         should be 20 bit format, positive, stored in a 32 bit signed integer
     * @param calibrationData constants from your specific peripherals mem, always size 3
     * @return temperature reading in degrees celcius
     */
    private static float compensateTemperature(int rawTemp, short[] calibrationData) {
        float digT1 = calibrationData[0];
        float digT2 = calibrationData[1];
        float digT3 = calibrationData[2];
        float adcT = (float) rawTemp;

        float varX1 = adcT / 16384f - digT1 / 1024f;
        float varX2 = varX1 * digT2;

        float varY1 = adcT / 131072f - digT1 / 8192f;
        float varY2 = varY1 * varY1;
        float varY3 = varY2 * digT3;

        return (varX2 + varY3) / 5120f;
    }
}

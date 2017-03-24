package com.blundell.tut;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private static final String I2C_ADDRESS = "I2C1";
    private static final int BMP280_TEMPERATURE_SENSOR_SLAVE = 0x77;

    private static final int REGISTER_TEMPERATURE_CALIBRATION_1 = 0x88;
    private static final int REGISTER_TEMPERATURE_CALIBRATION_2 = 0x8A;
    private static final int REGISTER_TEMPERATURE_CALIBRATION_3 = 0x8C;
    private static final int REGISTER_TEMPERATURE_RAW_VALUE_START = 0xFA;
    private static final int REGISTER_TEMPERATURE_RAW_VALUE_SIZE = 3;

    private final short[] calibrationData = new short[3];

    private I2cDevice bus;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PeripheralManagerService service = new PeripheralManagerService();
        try {
            bus = service.openI2cDevice(I2C_ADDRESS, BMP280_TEMPERATURE_SENSOR_SLAVE);
        } catch (IOException e) {
            throw new IllegalStateException(I2C_ADDRESS + " bus slave "
                                                + BMP280_TEMPERATURE_SENSOR_SLAVE + " connection cannot be opened.", e);
        }

        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onStart() {
        super.onStart();

        /**
         * The BMP280 output consists of the Analog to Digital Converter output values.
         * However, each sensing element behaves differently,
         * and actual temperature must be calculated using a set of calibration parameters.
         *
         * The calibration parameters are programmed into the devices’
         * non-volatile memory during production and cannot be altered.
         */
        try {
            calibrationData[0] = bus.readRegWord(REGISTER_TEMPERATURE_CALIBRATION_1);
            calibrationData[1] = bus.readRegWord(REGISTER_TEMPERATURE_CALIBRATION_2);
            calibrationData[2] = bus.readRegWord(REGISTER_TEMPERATURE_CALIBRATION_3);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read calibration data, can't read temperature without it.", e);
        }

        handler.post(readTemperature);
    }

    private final Runnable readTemperature = new Runnable() {
        @Override
        public void run() {
            byte[] data = new byte[REGISTER_TEMPERATURE_RAW_VALUE_SIZE];
            try {
                bus.readRegBuffer(REGISTER_TEMPERATURE_RAW_VALUE_START, data, REGISTER_TEMPERATURE_RAW_VALUE_SIZE);
            } catch (IOException e) {
                Log.e("TUT", "Cannot read temperature from bus.", e);
            }
            if (data.length != 0) {
                float temperature = compensateTemperature(readSample(data));
                Log.d("TUT", "Got temperature of: " + temperature);
            }

            handler.postDelayed(readTemperature, TimeUnit.SECONDS.toMillis(1));
        }

        private int readSample(byte[] data) {
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
         * @param rawTemp should be 20 bit format, positive, stored in a 32 bit signed integer
         * @return temperature reading in degrees celcius
         */
        private float compensateTemperature(int rawTemp) {
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
    };

    @Override
    protected void onStop() {
        handler.removeCallbacks(readTemperature);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        try {
            bus.close();
        } catch (IOException e) {
            Log.e("TUT", I2C_ADDRESS + " bus slave "
                + BMP280_TEMPERATURE_SENSOR_SLAVE + "connection cannot be closed, you may experience errors on next launch.", e);
        }
        super.onDestroy();
    }
}

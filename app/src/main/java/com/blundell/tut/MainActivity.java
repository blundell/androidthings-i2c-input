package com.blundell.tut;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManager;

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

        PeripheralManager service = PeripheralManager.getInstance();
        try {
            bus = service.openI2cDevice(I2C_ADDRESS, BMP280_TEMPERATURE_SENSOR_SLAVE);
        } catch (IOException e) {
            throw new IllegalStateException(I2C_ADDRESS + " bus slave "
                                                + BMP280_TEMPERATURE_SENSOR_SLAVE + " connection cannot be opened.", e);
        }

        try {
            calibrationData[0] = bus.readRegWord(REGISTER_TEMPERATURE_CALIBRATION_1);
            calibrationData[1] = bus.readRegWord(REGISTER_TEMPERATURE_CALIBRATION_2);
            calibrationData[2] = bus.readRegWord(REGISTER_TEMPERATURE_CALIBRATION_3);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read calibration data, can't read temperature without it.", e);
        }

        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onStart() {
        super.onStart();
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
                float temperature = Bmp280DataSheet.readTemperatureFrom(data, calibrationData);
                Log.d("TUT", "Got temperature of: " + temperature);
            }

            handler.postDelayed(readTemperature, TimeUnit.HOURS.toMillis(1));
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

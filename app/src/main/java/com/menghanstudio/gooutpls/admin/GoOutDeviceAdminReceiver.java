package com.menghanstudio.gooutpls.admin;

import android.app.admin.DeviceAdminReceiver;

/**
 * Admin component required for the adb device-owner provisioning step.
 * No device-admin policy is used beyond the device-owner uninstall capability.
 */
public class GoOutDeviceAdminReceiver extends DeviceAdminReceiver {
}

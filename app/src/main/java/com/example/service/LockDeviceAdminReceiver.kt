package com.example.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * DeviceAdminReceiver enabling 1-tap real hardware screen lock (power button behavior)
 */
class LockDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, LockDeviceAdminReceiver::class.java)
        }

        fun isDeviceAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            return dpm?.isAdminActive(getComponentName(context)) == true
        }

        fun lockDevice(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            return if (dpm != null && dpm.isAdminActive(getComponentName(context))) {
                dpm.lockNow()
                true
            } else {
                false
            }
        }

        fun requestDeviceAdminPrompt(context: Context) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Allows AssistiveTouch to lock your screen with 1 tap like the physical power button."
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}

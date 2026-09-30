#
# Copyright 2014 The Android Open-Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# This file is the build configuration for an aosp Android
# build for rockchip rk3528 hardware. This cleanly combines a set of
# device-specific aspects (drivers) with a device-agnostic
# product configuration (apps). Except for a few implementation
# details, it only fundamentally contains two inherit-product
# lines, aosp and rk3528, hence its name.

# First lunching is T, api_level is 33
PRODUCT_SHIPPING_API_LEVEL := 33
PRODUCT_OTA_ENFORCE_VINTF_KERNEL_REQUIREMENTS := false
PRODUCT_DTBO_TEMPLATE := $(LOCAL_PATH)/dt-overlay.in

include device/rockchip/common/build/rockchip/DynamicPartitions.mk
include device/rockchip/rk3528/rk3528_box/BoardConfig.mk
# This box image has no legacy sensors hardware module.
BOARD_HAS_SENSOR_HAL := false
DEVICE_MANIFEST_FILE := device/rockchip/rk3528/rk3528_box/manifest_a64_33.xml
include device/rockchip/common/BoardConfig.mk
# This box has no camera hardware; do not advertise camera HALs/features.
BOARD_CAMERA_SUPPORT := false
BOARD_CAMERA_SUPPORT_EXT := false
BOARD_CAMERA_SUPPORT_VIR := false
# Inherit from those products. Most specific first.
BOARD_USB_INIT_RC := device/rockchip/rk3528/rk3528_box/init.box-network-adb.rc
$(call inherit-product, device/rockchip/common/device.mk)
$(call inherit-product, device/rockchip/rk3528/product.mk)

DEVICE_PACKAGE_OVERLAYS += $(LOCAL_PATH)/../overlay

#TODO TV?
PRODUCT_CHARACTERISTICS := tv

# Linux 6.1 has no ashmem device; libcutils must use the memfd backend.
PRODUCT_SYSTEM_PROPERTIES += sys.use_memfd=true
$(call soong_config_set,android_hardware_audio,run_64bit,true)

PRODUCT_NAME := rk3528_box
PRODUCT_DEVICE := rk3528_box
PRODUCT_BRAND := RockChip
PRODUCT_MODEL := rk3528
PRODUCT_MANUFACTURER := RockChip
TARGET_BOOTLOADER_BOARD_NAME := RK3528

# Get the long list of APNs
PRODUCT_COPY_FILES += vendor/rockchip/common/phone/etc/apns-full-conf.xml:system/etc/apns-conf.xml
PRODUCT_COPY_FILES += vendor/rockchip/common/phone/etc/spn-conf.xml:system/etc/spn-conf.xml

PRODUCT_AAPT_CONFIG := normal large tvdpi hdpi
PRODUCT_AAPT_PREF_CONFIG := tvdpi

# TV Input HAL
PRODUCT_PACKAGES += \
    android.hardware.tv.input@1.0-impl

# Match the installed SystemUI wallpaper service requirement.
PRODUCT_PACKAGES += Rk3528BoxFrameworkOverlay

# This board uses the AIC8800 SDIO/UART combo controller.
PRODUCT_VENDOR_PROPERTIES += ro.vendor.bluetooth.library_name=libbt-vendor-aic.so

PRODUCT_COPY_FILES += device/rockchip/rk3528/rk3528_box/init.box-logging.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/init.box-logging.rc

PRODUCT_PACKAGES += BoxRemoteSetup

# Local appliance: network ADB is enabled without a touchscreen authorization flow.
PRODUCT_SYSTEM_PROPERTIES += ro.adb.secure=0

# Built against the RK3518 6.1.172 kernel; stable per-board Wi-Fi identity.

# Generated with the same kernel configuration by the public build script.
BOARD_VENDOR_KERNEL_MODULES += \
    device/rockchip/rk3528/rk3528_box/modules/aic8800_bsp.ko \
    device/rockchip/rk3528/rk3528_box/modules/aic8800_fdrv.ko

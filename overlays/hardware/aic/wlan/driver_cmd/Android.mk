LOCAL_PATH := $(call my-dir)

ifeq ($(WPA_SUPPLICANT_VERSION),VER_0_8_X)
ifneq ($(BOARD_WPA_SUPPLICANT_DRIVER),)
  CONFIG_DRIVER_$(BOARD_WPA_SUPPLICANT_DRIVER) := y
endif

WPA_SUPPL_DIR = external/wpa_supplicant_8
WPA_SRC_FILE :=
include $(WPA_SUPPL_DIR)/wpa_supplicant/android.config

WPA_SUPPL_DIR_INCLUDE = $(WPA_SUPPL_DIR)/src 	$(WPA_SUPPL_DIR)/src/common 	$(WPA_SUPPL_DIR)/src/drivers 	$(WPA_SUPPL_DIR)/src/l2_packet 	$(WPA_SUPPL_DIR)/src/utils 	$(WPA_SUPPL_DIR)/src/wps 	$(WPA_SUPPL_DIR)/wpa_supplicant

ifdef CONFIG_DRIVER_NL80211
WPA_SUPPL_DIR_INCLUDE += external/libnl/include
WPA_SRC_FILE += driver_cmd_nl80211.c
endif

ifdef CONFIG_ANDROID_LOG
L_CFLAGS += -DCONFIG_ANDROID_LOG
endif
L_CFLAGS += -Wall -Werror -Wno-unused-parameter -Wno-macro-redefined

include $(CLEAR_VARS)
LOCAL_MODULE := lib_driver_cmd_aic
LOCAL_SHARED_LIBRARIES := libc libcutils
LOCAL_CFLAGS := $(L_CFLAGS)
LOCAL_SRC_FILES := $(WPA_SRC_FILE)
LOCAL_C_INCLUDES := $(WPA_SUPPL_DIR_INCLUDE)
LOCAL_VENDOR_MODULE := true
# Called through a CFI-checked driver_ops function pointer in supplicant.
LOCAL_SANITIZE := cfi
include $(BUILD_STATIC_LIBRARY)

endif

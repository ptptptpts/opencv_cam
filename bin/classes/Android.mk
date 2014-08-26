LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#OpenCV
OPENCV_CAMERA_MODULES:=on
OPENCV_INSTALL_MODULES:=on

include ${OPENCV}/sdk/native/jni/OpenCV.mk

LOCAL_MODULE    := cam_activity
LOCAL_SRC_FILES := cam.cpp
LOCAL_LDLIBS    += -lm -llog -landroid
LOCAL_STATIC_LIBRARIES += android_native_app_glue

include $(BUILD_SHARED_LIBRARY)

$(call import-module,android/native_app_glue)

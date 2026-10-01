#pragma once

#include <cstdint>
#include <vector>
#include <libusb.h>

int get_subframe_size(const struct libusb_interface_descriptor *alt);
std::vector<uint32_t> get_uac1_sample_rates(const struct libusb_interface_descriptor *alt);

bool init_usb_dac_internal(int fd);
void close_usb_dac_internal();
void control_thread_func();
int LIBUSB_CALL hotplug_callback(libusb_context *ctx, libusb_device *device,
                                libusb_hotplug_event event, void *user_data);

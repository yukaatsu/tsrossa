#pragma once

#include <cstdint>
#include <set>
#include <vector>
#include <libusb.h>

int get_subframe_size(const struct libusb_interface_descriptor *alt);
std::vector<uint32_t> get_uac1_sample_rates(const struct libusb_interface_descriptor *alt);
int resolve_effective_usb_speed(libusb_device *dev, libusb_device_handle *handle, int uac_version, const std::set<uint32_t> &sample_rates);

bool init_usb_dac_internal(int fd);
void close_usb_dac_internal();
void control_thread_func();
void set_volume(float volume);
void set_hardware_volume_enabled(bool enabled);
bool is_hardware_volume_enabled();

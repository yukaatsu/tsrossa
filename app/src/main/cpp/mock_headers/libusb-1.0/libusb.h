#ifndef LIBUSB_H
#define LIBUSB_H
#define LIBUSB_CALL
typedef struct libusb_context libusb_context;
typedef struct libusb_device libusb_device;
typedef struct libusb_device_handle libusb_device_handle;
typedef struct libusb_transfer libusb_transfer;
typedef int libusb_hotplug_callback_handle;
typedef int libusb_hotplug_event;
#define LIBUSB_HOTPLUG_EVENT_DEVICE_LEFT 1
#define LIBUSB_HOTPLUG_NO_FLAGS 0
#define LIBUSB_HOTPLUG_MATCH_ANY -1
#define LIBUSB_CAP_HAS_HOTPLUG 1
#define LIBUSB_TRANSFER_COMPLETED 0
#define LIBUSB_TRANSFER_TIMED_OUT 1
#define LIBUSB_TRANSFER_CANCELLED 2
#define LIBUSB_TRANSFER_NO_DEVICE 3
#define LIBUSB_TRANSFER_ERROR 4
struct libusb_endpoint_descriptor {
    unsigned char bEndpointAddress;
    unsigned char bmAttributes;
    unsigned short wMaxPacketSize;
    unsigned char bInterval;
    unsigned char bRefresh;
    unsigned char bSynchAddress;
    const unsigned char *extra;
    int extra_length;
};
struct libusb_interface_descriptor {
    unsigned char bLength;
    unsigned char bDescriptorType;
    unsigned char bInterfaceNumber;
    unsigned char bAlternateSetting;
    unsigned char bNumEndpoints;
    unsigned char bInterfaceClass;
    unsigned char bInterfaceSubClass;
    unsigned char bInterfaceProtocol;
    unsigned char iInterface;
    const struct libusb_endpoint_descriptor *endpoint;
    const unsigned char *extra;
    int extra_length;
};
struct libusb_interface {
    const struct libusb_interface_descriptor *altsetting;
    int num_altsetting;
};
struct libusb_config_descriptor {
    unsigned char bLength;
    unsigned char bDescriptorType;
    unsigned short wTotalLength;
    unsigned char bNumInterfaces;
    unsigned char bConfigurationValue;
    unsigned char iConfiguration;
    unsigned char bmAttributes;
    unsigned char MaxPower;
    const struct libusb_interface *interface;
    const unsigned char *extra;
    int extra_length;
};
struct libusb_iso_packet_descriptor {
    unsigned int length;
    unsigned int actual_length;
    int status;
};
struct libusb_transfer {
    libusb_device_handle *dev_handle;
    unsigned char flags;
    unsigned char endpoint;
    unsigned char type;
    unsigned int timeout;
    int status;
    int length;
    int actual_length;
    void (*callback)(struct libusb_transfer *);
    void *user_data;
    unsigned char *buffer;
    int num_iso_packets;
    struct libusb_iso_packet_descriptor iso_packet_desc[0];
};

enum libusb_option {
    LIBUSB_OPTION_LOG_LEVEL = 0,
    LIBUSB_OPTION_USE_USBDK = 1,
    LIBUSB_OPTION_NO_DEVICE_DISCOVERY = 2,
    LIBUSB_OPTION_LOG_CB = 3,
    LIBUSB_OPTION_MAX = 4
};

struct libusb_init_option {
    enum libusb_option option;
    union {
        int ival;
        void *log_cbval;
    } value;
};

int libusb_init(libusb_context **ctx);
int libusb_init_context(libusb_context **ctx, const struct libusb_init_option options[], int num_options);
int libusb_set_option(libusb_context *ctx, enum libusb_option option, ...);
int libusb_has_capability(int capability);
int libusb_hotplug_register_callback(libusb_context *ctx, int events, int flags, int vendor_id, int product_id, int dev_class, void* cb, void *user_data, libusb_hotplug_callback_handle *handle);
int libusb_wrap_sys_device(libusb_context *ctx, intptr_t sys_dev, libusb_device_handle **dev_handle);
libusb_device* libusb_get_device(libusb_device_handle *dev_handle);
int libusb_get_active_config_descriptor(libusb_device *dev, struct libusb_config_descriptor **config);
void libusb_free_config_descriptor(struct libusb_config_descriptor *config);
int libusb_get_max_iso_packet_size(libusb_device *dev, unsigned char endpoint);
int libusb_set_auto_detach_kernel_driver(libusb_device_handle *dev_handle, int enable);
int libusb_claim_interface(libusb_device_handle *dev_handle, int interface_number);
int libusb_release_interface(libusb_device_handle *dev_handle, int interface_number);
int libusb_set_interface_alt_setting(libusb_device_handle *dev_handle, int interface_number, int alternate_setting);
void libusb_close(libusb_device_handle *dev_handle);
struct libusb_transfer* libusb_alloc_transfer(int iso_packets);
void libusb_free_transfer(struct libusb_transfer *transfer);
void libusb_fill_iso_transfer(struct libusb_transfer *transfer, libusb_device_handle *dev_handle, unsigned char endpoint, unsigned char *buffer, int length, int num_iso_packets, void* callback, void *user_data, unsigned int timeout);
void libusb_set_iso_packet_lengths(struct libusb_transfer *transfer, unsigned int length);
int libusb_submit_transfer(struct libusb_transfer *transfer);
int libusb_handle_events_timeout_completed(libusb_context *ctx, void *tv, int *completed);
int libusb_cancel_transfer(struct libusb_transfer *transfer);
void libusb_hotplug_deregister_callback(libusb_context *ctx, libusb_hotplug_callback_handle handle);
void libusb_exit(libusb_context *ctx);
#endif

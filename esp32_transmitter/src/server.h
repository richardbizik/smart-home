#include <esp_http_server.h>
#include <sys/param.h>

#ifndef SERVER_H
#define SERVER_H

httpd_handle_t start_webserver(void);
#endif

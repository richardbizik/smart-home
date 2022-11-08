#include <distance.h>
#include <esp_http_server.h>
#include <presence.h>
#include <sys/param.h>
#include <transmit.h>

/* URI handler function to be called during GET /info request */
esp_err_t get_info(httpd_req_t *req) {
  String s = "{\"distance\":";
  s.concat(getDistance());
  s.concat(", \"presence\":");
  if (getPresence() == 1) {
    s.concat("true}");
  } else {
    s.concat("false}");
  }
  httpd_resp_set_type(req, "application/json");
  httpd_resp_send(req, s.begin(), HTTPD_RESP_USE_STRLEN);
  return ESP_OK;
}
/* URI handler function to be called during POST /transmit request */
esp_err_t post_transmit(httpd_req_t *req) {
  /* Destination buffer for content of HTTP POST request.
   * httpd_req_recv() accepts char* only, but content could
   * as well be any binary data (needs type casting).
   * In case of string data, null termination will be absent, and
   * content length would give length of string */
  if (req->content_len > 500) {
    httpd_resp_set_status(req, "400");
    const char resp[] = "request body too long";
    httpd_resp_send(req, resp, HTTPD_RESP_USE_STRLEN);
    return ESP_OK;
  }
  char content[req->content_len];

  int ret = httpd_req_recv(req, content, req->content_len);
  if (ret <= 0) { /* 0 return value indicates connection closed */
    /* Check if timeout occurred */
    if (ret == HTTPD_SOCK_ERR_TIMEOUT) {
      /* In case of timeout one can choose to retry calling
       * httpd_req_recv(), but to keep it simple, here we
       * respond with an HTTP 408 (Request Timeout) error */
      httpd_resp_send_408(req);
    }
    /* In case of error, returning ESP_FAIL will
     * ensure that the underlying socket is closed */
    return ESP_FAIL;
  }
  Serial.print("Received http request:");
  Serial.println(content);
  Serial.print("With content length:");
  Serial.println(req->content_len);

  int tr = transmit(content);
  if (tr < 0) {
    httpd_resp_set_status(req, "400");
  } else {
    httpd_resp_set_status(req, "200");
  }
  const char resp[] = "";
  httpd_resp_send(req, resp, HTTPD_RESP_USE_STRLEN);
  return ESP_OK;
}

/* URI handler structure for POST /transmit */
httpd_uri_t uri_post_transmit = {.uri = "/transmit",
                                 .method = HTTP_POST,
                                 .handler = post_transmit,
                                 .user_ctx = NULL};
httpd_uri_t uri_get_info = {
    .uri = "/info", .method = HTTP_GET, .handler = get_info, .user_ctx = NULL};

/* Function for starting the webserver */
httpd_handle_t start_webserver(void) {
  /* Generate default configuration */
  httpd_config_t config = HTTPD_DEFAULT_CONFIG();

  /* Empty handle to esp_http_server */
  httpd_handle_t server = NULL;

  /* Start the httpd server */
  if (httpd_start(&server, &config) == ESP_OK) {
    /* Register URI handlers */
    httpd_register_uri_handler(server, &uri_post_transmit);
    httpd_register_uri_handler(server, &uri_get_info);
  }
  /* If server failed to start, handle will be NULL */
  return server;
}

/* Function for stopping the webserver */
void stop_webserver(httpd_handle_t server) {
  if (server) {
    /* Stop the httpd server */
    httpd_stop(server);
  }
}

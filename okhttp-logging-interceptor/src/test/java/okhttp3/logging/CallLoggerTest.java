/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3.logging;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import okhttp3.tls.HandshakeCertificates;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static okhttp3.tls.internal.TlsUtil.localhost;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class CallLoggerTest {
  private static final MediaType PLAIN = MediaType.get("text/plain");

  @Rule public final MockWebServer server = new MockWebServer();

  private final HandshakeCertificates handshakeCertificates = localhost();
  private OkHttpClient client;
  private HttpUrl url;

  private final LogRecorder logRecorder = new LogRecorder();
  private final CallLogger callLogger = new CallLogger(logRecorder);

  @Before
  public void setUp() {
    client =
        new OkHttpClient.Builder()
            .eventListenerFactory(callLogger.eventListenerFactory())
            .sslSocketFactory(
                handshakeCertificates.sslSocketFactory(), handshakeCertificates.trustManager())
            .build();

    url = server.url("/");
  }

  @Test
  public void get() throws Exception {
    server.enqueue(new MockResponse().setBody("Hello!").setHeader("Content-Type", PLAIN));
    Response response = client.newCall(request().build()).execute();
    response.body().bytes();

    logRecorder
        .assertLogMatch(
            "\\* callStart: Request\\{method=GET, url=http://localhost:.+, tags=\\{\\}\\}")
        .assertLogEqual("* dnsStart: localhost")
        .assertLogMatch("\\* dnsEnd: \\[.+\\]")
        .assertLogMatch("\\* connectStart: localhost/.+ DIRECT")
        .assertLogEqual("* connectEnd: http/1.1")
        .assertLogMatch(
            "\\* connectionAcquired: Connection\\{localhost:.+, proxy=DIRECT hostAddress=localhost/.+ cipherSuite=none protocol=http/1\\.1\\}")
        .assertLogEqual("* requestHeadersStart")
        .assertLogEqual("* requestHeadersEnd")
        .assertLogEqual("* responseHeadersStart")
        .assertLogMatch(
            "\\* responseHeadersEnd: Response\\{protocol=http/1\\.1, code=200, message=OK, url=http://localhost.+}")
        .assertLogEqual("* responseBodyStart")
        .assertLogEqual("* responseBodyEnd: byteCount=6")
        .assertLogEqual("* connectionReleased")
        .assertLogMatch("\\* callEnd \\(took \\d+ms\\)")
        .assertNoMoreLogs();
  }

  @Test
  public void post() throws IOException {
    server.enqueue(new MockResponse());
    client.newCall(request().post(RequestBody.create(PLAIN, "Hello!")).build()).execute();

    logRecorder
        .assertLogMatch(
            "\\* callStart: Request\\{method=POST, url=http://localhost:.+, tags=\\{\\}\\}")
        .assertLogEqual("* dnsStart: localhost")
        .assertLogMatch("\\* dnsEnd: \\[.+\\]")
        .assertLogMatch("\\* connectStart: localhost/.+ DIRECT")
        .assertLogEqual("* connectEnd: http/1.1")
        .assertLogMatch(
            "\\* connectionAcquired: Connection\\{localhost:.+, proxy=DIRECT hostAddress=localhost/.+ cipherSuite=none protocol=http/1\\.1\\}")
        .assertLogEqual("* requestHeadersStart")
        .assertLogEqual("* requestHeadersEnd")
        .assertLogEqual("* requestBodyStart")
        .assertLogEqual("* requestBodyEnd: byteCount=6")
        .assertLogEqual("* responseHeadersStart")
        .assertLogMatch(
            "\\* responseHeadersEnd: Response\\{protocol=http/1\\.1, code=200, message=OK, url=http://localhost.+}")
        .assertLogEqual("* responseBodyStart")
        .assertLogEqual("* responseBodyEnd: byteCount=0")
        .assertLogEqual("* connectionReleased")
        .assertLogMatch("\\* callEnd \\(took \\d+ms\\)")
        .assertNoMoreLogs();
  }

  @Test
  public void secureGet() throws Exception {
    server.useHttps(handshakeCertificates.sslSocketFactory(), false);
    url = server.url("/");

    server.enqueue(new MockResponse());
    Response response = client.newCall(request().build()).execute();
    response.body().bytes();

    logRecorder
        .assertLogMatch(
            "\\* callStart: Request\\{method=GET, url=https://localhost:.+, tags=\\{\\}\\}")
        .assertLogEqual("* dnsStart: localhost")
        .assertLogMatch("\\* dnsEnd: \\[.+\\]")
        .assertLogMatch("\\* connectStart: localhost/.+ DIRECT")
        .assertLogEqual("* secureConnectStart")
        .assertLogEqual("* secureConnectEnd")
        .assertLogEqual("* connectEnd: h2")
        .assertLogMatch(
            "\\* connectionAcquired: Connection\\{localhost:.+, proxy=DIRECT hostAddress=localhost/.+ cipherSuite=TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384 protocol=h2\\}")
        .assertLogEqual("* requestHeadersStart")
        .assertLogEqual("* requestHeadersEnd")
        .assertLogEqual("* responseHeadersStart")
        .assertLogMatch(
            "\\* responseHeadersEnd: Response\\{protocol=h2, code=200, message=, url=https://localhost.+\\}")
        .assertLogEqual("* responseBodyStart")
        .assertLogEqual("* responseBodyEnd: byteCount=0")
        .assertLogEqual("* connectionReleased")
        .assertLogMatch("\\* callEnd \\(took \\d+ms\\)")
        .assertNoMoreLogs();
  }

  @Test
  public void dnsFail() throws IOException {
    client =
        new OkHttpClient.Builder()
            .dns(
                new Dns() {
                  @Override
                  public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                    throw new UnknownHostException("reason");
                  }
                })
            .eventListenerFactory(callLogger.eventListenerFactory())
            .build();

    try {
      client.newCall(request().build()).execute();
      fail();
    } catch (UnknownHostException expected) {
    }

    logRecorder
        .assertLogMatch(
            "\\* callStart: Request\\{method=GET, url=http://localhost:.+, tags=\\{\\}\\}")
        .assertLogEqual("* dnsStart: localhost")
        .assertLogEqual("* callFailed: java.net.UnknownHostException: reason")
        .assertNoMoreLogs();
  }

  @Test
  public void connectFail() {
    server.useHttps(handshakeCertificates.sslSocketFactory(), false);
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE));
    url = server.url("/");

    try {
      client.newCall(request().build()).execute();
      fail();
    } catch (IOException expected) {
    }

    logRecorder
        .assertLogMatch(
            "\\* callStart: Request\\{method=GET, url=https://localhost:.+, tags=\\{\\}\\}")
        .assertLogEqual("* dnsStart: localhost")
        .assertLogMatch("\\* dnsEnd: \\[.+\\]")
        .assertLogMatch("\\* connectStart: localhost/.+ DIRECT")
        .assertLogEqual("* secureConnectStart")
        .assertLogMatch(
            "\\* connectFailed: null javax\\.net\\.ssl\\.SSLProtocolException: Handshake message sequence violation, 1")
        .assertLogMatch("\\* connectStart: localhost/.+ DIRECT")
        .assertLogMatch(
            "\\* connectFailed: null java.net.ConnectException: Failed to connect to localhost/.+")
        .assertLogMatch(
            "\\* callFailed: java.net.ConnectException: Failed to connect to localhost/.+")
        .assertNoMoreLogs();
  }

  private Request.Builder request() {
    return new Request.Builder().url(url);
  }

  private static class LogRecorder implements CallLogger.Logger {
    private final List<String> logs = new ArrayList<>();
    private int index;

    LogRecorder assertLogEqual(String expected) {
      assertTrue("No more messages found", index < logs.size());
      String actual = logs.get(index++);
      assertEquals(expected, actual);
      return this;
    }

    LogRecorder assertLogMatch(String pattern) {
      assertTrue("No more messages found", index < logs.size());
      String actual = logs.get(index++);
      assertTrue(
          "<" + actual + "> did not match pattern <" + pattern + ">",
          Pattern.matches(pattern, actual));
      return this;
    }

    void assertNoMoreLogs() {
      assertEquals("More messages remain: " + logs.subList(index, logs.size()), index, logs.size());
    }

    @Override
    public void log(String message) {
      logs.add(message);
    }
  }
}

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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.platform.Platform;

import static okhttp3.internal.platform.Platform.INFO;

/**
 * A logger of HTTP calls. Can be applied as an {@linkplain OkHttpClient#eventListenerFactory()
 * event listener factory}.
 *
 * <p>The format of the logs created by this class should not be considered stable and may change
 * slightly between releases. If you need a stable logging format, use your own event listener.
 */
public final class CallLogger {
  public interface Logger {
    void log(String message);

    /** A {@link Logger} defaults output appropriate for the current platform. */
    Logger DEFAULT =
        new Logger() {
          @Override
          public void log(String message) {
            Platform.get().log(INFO, message, null);
          }
        };
  }

  public CallLogger() {
    this(Logger.DEFAULT);
  }

  public CallLogger(Logger logger) {
    this.logger = logger;
  }

  private final Logger logger;

  public EventListener.Factory eventListenerFactory() {
    return new EventListener.Factory() {
      public EventListener create(Call call) {
        return new CallLoggerEventListener();
      }
    };
  }

  private final class CallLoggerEventListener extends EventListener {
    private long startNs;

    @Override
    public void callStart(Call call) {
      startNs = System.nanoTime();

      logger.log("* callStart: " + call.request());
    }

    @Override
    public void dnsStart(Call call, String domainName) {
      logger.log("* dnsStart: " + domainName);
    }

    @Override
    public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
      logger.log("* dnsEnd: " + inetAddressList);
    }

    @Override
    public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
      logger.log("* connectStart: " + inetSocketAddress + " " + proxy);
    }

    @Override
    public void secureConnectStart(Call call) {
      logger.log("* secureConnectStart");
    }

    @Override
    public void secureConnectEnd(Call call, @Nullable Handshake handshake) {
      logger.log("* secureConnectEnd");
    }

    @Override
    public void connectEnd(
        Call call, InetSocketAddress inetSocketAddress, Proxy proxy, @Nullable Protocol protocol) {
      logger.log("* connectEnd: " + protocol);
    }

    @Override
    public void connectFailed(
        Call call,
        InetSocketAddress inetSocketAddress,
        Proxy proxy,
        @Nullable Protocol protocol,
        IOException ioe) {
      logger.log("* connectFailed: " + protocol + " " + ioe);
    }

    @Override
    public void connectionAcquired(Call call, Connection connection) {
      logger.log("* connectionAcquired: " + connection);
    }

    @Override
    public void connectionReleased(Call call, Connection connection) {
      logger.log("* connectionReleased");
    }

    @Override
    public void requestHeadersStart(Call call) {
      logger.log("* requestHeadersStart");
    }

    @Override
    public void requestHeadersEnd(Call call, Request request) {
      logger.log("* requestHeadersEnd");
    }

    @Override
    public void requestBodyStart(Call call) {
      logger.log("* requestBodyStart");
    }

    @Override
    public void requestBodyEnd(Call call, long byteCount) {
      logger.log("* requestBodyEnd: byteCount=" + byteCount);
    }

    @Override
    public void responseHeadersStart(Call call) {
      logger.log("* responseHeadersStart");
    }

    @Override
    public void responseHeadersEnd(Call call, Response response) {
      logger.log("* responseHeadersEnd: " + response);
    }

    @Override
    public void responseBodyStart(Call call) {
      logger.log("* responseBodyStart");
    }

    @Override
    public void responseBodyEnd(Call call, long byteCount) {
      logger.log("* responseBodyEnd: byteCount=" + byteCount);
    }

    @Override
    public void callEnd(Call call) {
      long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
      logger.log("* callEnd (took " + tookMs + "ms)");
    }

    @Override
    public void callFailed(Call call, IOException ioe) {
      logger.log("* callFailed: " + ioe);
    }
  }
}

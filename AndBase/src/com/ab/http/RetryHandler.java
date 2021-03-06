/*
 * Copyright (C) 2013 www.418log.org
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.ab.http;

import android.os.SystemClock;

import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;

import javax.net.ssl.SSLException;

// TODO: Auto-generated Javadoc
/**
 * The Class RetryHandler.
 */
class RetryHandler implements HttpRequestRetryHandler {
    
    /** The exception whitelist. */
    private static HashSet<Class<?>> exceptionWhitelist = new HashSet<Class<?>>();
    
    /** The exception blacklist. */
    private static HashSet<Class<?>> exceptionBlacklist = new HashSet<Class<?>>();

    static {
        // Retry if the server dropped connection on us
        exceptionWhitelist.add(NoHttpResponseException.class);
        // retry-this, since it may happens as part of a Wi-Fi to 3G failover
        exceptionWhitelist.add(UnknownHostException.class);
        // retry-this, since it may happens as part of a Wi-Fi to 3G failover
        exceptionWhitelist.add(SocketException.class);

        // never retry timeouts
        exceptionBlacklist.add(InterruptedIOException.class);
        // never retry SSL handshake failures
        exceptionBlacklist.add(SSLException.class);
    }

    /** The max retries. */
    private final int maxRetries;
    
    /** The retry sleep time ms. */
    private final int retrySleepTimeMS;

    /**
     * Instantiates a new retry handler.
     *
     * @param maxRetries the max retries
     * @param retrySleepTimeMS the retry sleep time ms
     */
    public RetryHandler(int maxRetries, int retrySleepTimeMS) {
        this.maxRetries = maxRetries;
        this.retrySleepTimeMS = retrySleepTimeMS;
    }

    /**
     * 描述：TODO
     * @see org.apache.http.client.HttpRequestRetryHandler#retryRequest(java.io.IOException, int, org.apache.http.protocol.HttpContext)
     * @author: zhaoqp
     * @date：2013-10-22 下午4:23:15
     * @version v1.0
     */
    @Override
    public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
        boolean retry = true;

        Boolean b = (Boolean) context.getAttribute(ExecutionContext.HTTP_REQ_SENT);
        boolean sent = (b != null && b);

        if (executionCount > maxRetries) {
            // Do not retry if over max retry count
            retry = false;
        } else if (isInList(exceptionBlacklist, exception)) {
            // immediately cancel retry if the error is blacklisted
            retry = false;
        } else if (isInList(exceptionWhitelist, exception)) {
            // immediately retry if error is whitelisted
            retry = true;
        } else if (!sent) {
            // for most other errors, retry only if request hasn't been fully sent yet
            retry = true;
        }

        if (retry) {
            // resend all idempotent requests
            HttpUriRequest currentReq = (HttpUriRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
            if (currentReq == null) {
                return false;
            }
            String requestType = currentReq.getMethod();
            retry = !requestType.equals("POST");
        }

        if (retry) {
            SystemClock.sleep(retrySleepTimeMS);
        } else {
            exception.printStackTrace();
        }

        return retry;
    }

    /**
     * Checks if is in list.
     *
     * @param list the list
     * @param error the error
     * @return true, if is in list
     */
    protected boolean isInList(HashSet<Class<?>> list, Throwable error) {
        for (Class<?> aList : list) {
            if (aList.isInstance(error)) {
                return true;
            }
        }
        return false;
    }
}

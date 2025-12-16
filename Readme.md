# Apache HTTP server benchmarking tool

- ab -c 10 -n 1000000 http://localhost:8080/test/new

# Apache Httpclient5 相关的内存泄漏风险点
## org.springframework.http.client.HttpComponentsClientHttpRequestFactory
```
    /**
     * Shutdown hook that closes the underlying {@link HttpClientConnectionManager}'s
     * connection pool, if any.
     */
    @Override
    public void destroy() throws Exception {
        HttpClient httpClient = getHttpClient();
        if (httpClient instanceof Closeable closeable) {
            closeable.close();
        }
    }
```
## org.apache.hc.client5.http.impl.classic.InternalHttpClient
```
    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (this.closeables != null) {
            Closeable closeable;
            while ((closeable = this.closeables.poll()) != null) {
                try {
                    if (closeable instanceof ModalCloseable) {
                        ((ModalCloseable) closeable).close(closeMode);
                    } else {
                        closeable.close();
                    }
                } catch (final IOException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }
```

# Apache Httpclient4 相关的内存泄漏风险点
1. 


# Apache HTTP server benchmarking tool
- ab -c 10 -n 1000000 http://localhost:8080/test/new

# 资源泄露的原因
- 应用创建的应用层对象（内存new/free）在某个地方Hold住了，比如静态对象
- 应用创建的系统层对象（线程start/interrupt，OS资源等）没有主动及时释放

# 如何确定类是否有资源泄露
- 实现： DisposableBean/Closeable
- 关键字： finalize/destroy/close/shutdown/interrupt/stop/start/Thread/Executor

# Apache Httpclient5 相关的资源泄漏风险点
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
## org.apache.hc.client5.http.impl.IdleConnectionEvictor
```
public void shutdown() {
    thread.interrupt();
}
```
## org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
```
    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (this.closed.compareAndSet(false, true)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Shutdown connection pool {}", closeMode);
            }
            this.pool.close(closeMode);
            LOG.debug("Connection pool shut down");
        }
    }
```
## org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager
```
    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }
    
    @Override
    public void close(final CloseMode closeMode) {
        if (this.closed.compareAndSet(false, true)) {
            closeConnection(closeMode);
        }
    }
```

# Apache Httpclient4 相关的资源泄漏风险点
## org.springframework.http.client.HttpComponentsClientHttpRequestFactory
```
	/**
	 * Shutdown hook that closes the underlying
	 * {@link org.apache.http.conn.HttpClientConnectionManager ClientConnectionManager}'s
	 * connection pool, if any.
	 */
	@Override
	public void destroy() throws Exception {
		HttpClient httpClient = getHttpClient();
		if (httpClient instanceof Closeable) {
			((Closeable) httpClient).close();
		}
	}
```
## org.apache.http.impl.client.InternalHttpClient
```
    @Override
    public void close() {
        if (this.closeables != null) {
            for (final Closeable closeable: this.closeables) {
                try {
                    closeable.close();
                } catch (final IOException ex) {
                    this.log.error(ex.getMessage(), ex);
                }
            }
        }
    }
```

## org.apache.http.impl.client.IdleConnectionEvictor
```
    public void shutdown() {
        thread.interrupt();
    }
```
## org.apache.http.impl.conn.PoolingHttpClientConnectionManager
```
    @Override
    protected void finalize() throws Throwable {
        try {
            shutdown();
        } finally {
            super.finalize();
        }
    }
    
    @Override
    public void close() {
        shutdown();
    }
    
    @Override
    public void shutdown() {
        if (this.isShutDown.compareAndSet(false, true)) {
            this.log.debug("Connection manager is shutting down");
            try {
                this.pool.enumLeased(new PoolEntryCallback<HttpRoute, ManagedHttpClientConnection>() {

                    @Override
                    public void process(final PoolEntry<HttpRoute, ManagedHttpClientConnection> entry) {
                        final ManagedHttpClientConnection connection = entry.getConnection();
                        if (connection != null) {
                            try {
                                connection.shutdown();
                            } catch (final IOException iox) {
                                if (log.isDebugEnabled()) {
                                    log.debug("I/O exception shutting down connection", iox);
                                }
                            }
                        }
                    }

                });
                this.pool.shutdown();
            } catch (final IOException ex) {
                this.log.debug("I/O exception shutting down connection manager", ex);
            }
            this.log.debug("Connection manager shut down");
        }
    }
```
## org.apache.http.impl.conn.BasicHttpClientConnectionManager
```
    @Override
    protected void finalize() throws Throwable {
        try {
            shutdown();
        } finally { // Make sure we call overridden method even if shutdown barfs
            super.finalize();
        }
    }

    @Override
    public void close() {
        if (this.isShutdown.compareAndSet(false, true)) {
            closeConnection();
        }
    }
    
    @Override
    public void shutdown() {
        if (this.isShutdown.compareAndSet(false, true)) {
            if (this.conn != null) {
                this.log.debug("Shutting down connection");
                try {
                    this.conn.shutdown();
                } catch (final IOException iox) {
                    if (this.log.isDebugEnabled()) {
                        this.log.debug("I/O exception shutting down connection", iox);
                    }
                }
                this.conn = null;
            }
        }
    }
  
    private synchronized void closeConnection() {
        if (this.conn != null) {
            this.log.debug("Closing connection");
            try {
                this.conn.close();
            } catch (final IOException iox) {
                if (this.log.isDebugEnabled()) {
                    this.log.debug("I/O exception closing connection", iox);
                }
            }
            this.conn = null;
        }
    }
```


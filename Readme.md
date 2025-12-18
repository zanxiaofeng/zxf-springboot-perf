# Apache HTTP server benchmarking tool
- ab -c 10 -n 1000000 http://localhost:8080/httpclient/new/default
- ab -c 10 -n 1000000 http://localhost:8080/httpclient/new/custom/pool

# 资源泄露的原因
- 应用层对象（内存new/free）创建后在某个地方引用了导致不能释放，比如类静态变量引用，线程局部变量引用
- 系统层对象（线程start/interrupt，线程池，OS资源等）创建后没有主动及时释放

# 如何确定类是否有资源泄露
- 实现： DisposableBean/Closeable
- 关键字： finalize/destroy/close/shutdown/interrupt/stop/start/Thread/Executor

# Apache Httpclient5(5.5.1) 相关的资源泄漏风险点
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
## org.apache.hc.client5.http.impl.IdleConnectionEvictor(In InternalHttpClient's closeables)
```
public void shutdown() {
    thread.interrupt();
}
```
## org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager(In InternalHttpClient's closeables)
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
## org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager(In InternalHttpClient's closeables)
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
## org.apache.hc.client5.http.impl.io.DefaultManagedHttpClientConnection
```
    @Override
    public void close(final CloseMode closeMode) {
        if (this.closed.compareAndSet(false, true)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} close connection {}", this.id, closeMode);
            }
            super.close(closeMode);
        }
    }
    @Override
    public void close() throws IOException {
        if (this.closed.compareAndSet(false, true)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Close connection", this.id);
            }
            super.close();
        }
    }
```
## org.apache.hc.core5.http.impl.io.BHttpConnectionBase
```
    public void close() throws IOException {
        SocketHolder socketHolder = (SocketHolder)this.socketHolderRef.getAndSet((Object)null);
        if (socketHolder != null) {
            try (Socket baseSocket = socketHolder.getBaseSocket()) {
                this.inBuffer.clear();
                this.outbuffer.flush(socketHolder.getOutputStream());
                SSLSocket sslSocket = socketHolder.getSSLSocket();
                if (sslSocket != null) {
                    sslSocket.close();
                }
            }
        }

    }

    public void close(CloseMode closeMode) {
        SocketHolder socketHolder = (SocketHolder)this.socketHolderRef.getAndSet((Object)null);
        if (socketHolder != null) {
            SSLSocket sslSocket = socketHolder.getSSLSocket();
            Socket baseSocket = socketHolder.getBaseSocket();
            if (closeMode == CloseMode.IMMEDIATE) {
                try {
                    baseSocket.setSoLinger(true, 0);
                } catch (IOException var21) {
                } finally {
                    Closer.closeQuietly(baseSocket);
                }
            } else {
                try {
                    if (sslSocket != null) {
                        try {
                            try {
                                if (!sslSocket.isOutputShutdown()) {
                                    sslSocket.shutdownOutput();
                                }

                                if (!sslSocket.isInputShutdown()) {
                                    sslSocket.shutdownInput();
                                }
                            } catch (UnsupportedOperationException var18) {
                            }

                            sslSocket.close();
                        } catch (IOException var19) {
                        }
                    }
                } finally {
                    Closer.closeQuietly(baseSocket);
                }
            }
        }
    }
```

# Apache Httpclient4(4.5.14) 相关的资源泄漏风险点
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

## org.apache.http.impl.client.IdleConnectionEvictor(In InternalHttpClient's closeables)
```
    public void shutdown() {
        thread.interrupt();
    }
```
## org.apache.http.impl.conn.PoolingHttpClientConnectionManager(In InternalHttpClient's closeables)
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
## org.apache.http.impl.conn.BasicHttpClientConnectionManager(In InternalHttpClient's closeables)
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
## org.apache.http.impl.conn.CPool(In PoolingHttpClientConnectionManager)
```
    /**
     * Shuts down the pool.
     */
    public void shutdown() throws IOException {
        if (this.isShutDown) {
            return ;
        }
        this.isShutDown = true;
        this.lock.lock();
        try {
            for (final E entry: this.available) {
                entry.close();
            }
            for (final E entry: this.leased) {
                entry.close();
            }
            for (final RouteSpecificPool<T, C, E> pool: this.routeToPool.values()) {
                pool.shutdown();
            }
            this.routeToPool.clear();
            this.leased.clear();
            this.available.clear();
        } finally {
            this.lock.unlock();
        }
    }
```
## org.apache.http.impl.conn.CPoolEntry
```
    @Override
    public void close() {
        try {
            closeConnection();
        } catch (final IOException ex) {
            this.log.debug("I/O error closing connection", ex);
        }
    }
    
    public void closeConnection() throws IOException {
        final HttpClientConnection conn = getConnection();
        conn.close();
    }
```

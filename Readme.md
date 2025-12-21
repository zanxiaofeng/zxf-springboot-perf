# Apache HTTP server benchmarking tool
- ab -c 10 -n 1000000 http://localhost:8080/template/new/default
- ab -c 10 -n 1000000 http://localhost:8080/template/new/custom/pool
- ab -c 10 -n 1000000 http://localhost:8080/httpclient/new/default?close=false

# 资源泄露的原因
- 应用层对象（内存资源，new/free）创建后在某个地方引用了导致不能释放，比如类静态变量引用，线程局部变量引用
- 系统层对象（线程start/interrupt，线程池shutdown，文件描述符open/close，JNI资源，图形和GUI资源）创建后没有主动及时释放

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
## How TCP socket was closed in apache httpclient5?
```
java.base/sun.nio.ch.NioSocketImpl.close(NioSocketImpl.java:877), 
java.base/java.net.SocksSocketImpl.close(SocksSocketImpl.java:556), 
java.base/java.net.Socket.close(Socket.java:1756), 
org.apache.hc.core5.io.Closer.close(Closer.java:48), 
org.apache.hc.core5.io.Closer.closeQuietly(Closer.java:71), 
org.apache.hc.core5.http.impl.io.BHttpConnectionBase.close(BHttpConnectionBase.java:268), 
org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnection.close(DefaultBHttpClientConnection.java:71), 
org.apache.hc.client5.http.impl.io.DefaultManagedHttpClientConnection.close(DefaultManagedHttpClientConnection.java:176), 
org.apache.hc.core5.pool.DefaultDisposalCallback.execute(DefaultDisposalCallback.java:55), 
org.apache.hc.core5.pool.DefaultDisposalCallback.execute(DefaultDisposalCallback.java:42), 
org.apache.hc.core5.pool.PoolEntry.discardConnection(PoolEntry.java:178), 
org.apache.hc.core5.pool.StrictConnPool$PerRoutePool.shutdown(StrictConnPool.java:839), 
org.apache.hc.core5.pool.StrictConnPool.close(StrictConnPool.java:142), 
org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager.close(PoolingHttpClientConnectionManager.java:281), 
org.apache.hc.client5.http.impl.classic.InternalHttpClient.close(InternalHttpClient.java:209), 
org.apache.hc.client5.http.impl.classic.InternalHttpClient.close(InternalHttpClient.java:199), 
zxf.perf.app.control.TestController.testHttpClient(TestController.java:71), 
zxf.perf.app.control.TestController.newHttpClientDefault(TestController.java:49), 
java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103), 
java.base/java.lang.reflect.Method.invoke(Method.java:580), 
org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:258), 
org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:191), 
org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:118), 
org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:991), 
org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:896), 
org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87), 
org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1089), 
org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:979), 
org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014), 
org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:903), 
jakarta.servlet.http.HttpServlet.service(HttpServlet.java:564), 
org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:885), 
jakarta.servlet.http.HttpServlet.service(HttpServlet.java:658), 
org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193), 
org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138), 
org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:51), 
org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162), 
org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138), 
org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100), 
org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116), 
org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162), 
org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138), 
org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93), 
org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116), 
org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162), 
org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138), 
org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201), 
org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116), 
org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162), 
org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138), 
org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:165), 
org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:88), 
org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:482), 
org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:113), 
org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:83), 
org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:72), 
org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:342), 
org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:399), 
org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:63), 
org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:903), 
org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1774), 
org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:52), 
org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:973), 
org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:491), 
org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:63), 
java.base/java.lang.Thread.run(Thread.java:1570)
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

## How TCP socket was closed in apache httpclient4?
```
java.base/java.net.Socket.close(Socket.java:1751), 
org.apache.http.impl.BHttpConnectionBase.close(BHttpConnectionBase.java:320), 
org.apache.http.impl.conn.LoggingManagedHttpClientConnection.close(LoggingManagedHttpClientConnection.java:81), 
org.apache.http.impl.conn.CPoolEntry.closeConnection(CPoolEntry.java:70), 
org.apache.http.impl.conn.CPoolEntry.close(CPoolEntry.java:96), 
org.apache.http.pool.AbstractConnPool.shutdown(AbstractConnPool.java:149), 
org.apache.http.impl.conn.PoolingHttpClientConnectionManager.shutdown(PoolingHttpClientConnectionManager.java:430), 
org.apache.http.impl.conn.PoolingHttpClientConnectionManager.finalize(PoolingHttpClientConnectionManager.java:207), 
java.base/java.lang.System$2.invokeFinalize(System.java:2392), 
java.base/java.lang.ref.Finalizer.runFinalizer(Finalizer.java:96), 
java.base/java.lang.ref.Finalizer$FinalizerThread.run(Finalizer.java:174)
```
```
java.base/java.net.Socket.close(Socket.java:1751), 
org.apache.http.impl.BHttpConnectionBase.close(BHttpConnectionBase.java:320), 
org.apache.http.impl.conn.LoggingManagedHttpClientConnection.close(LoggingManagedHttpClientConnection.java:81), 
org.apache.http.impl.conn.CPoolEntry.closeConnection(CPoolEntry.java:70), 
org.apache.http.impl.conn.CPoolEntry.close(CPoolEntry.java:96), 
org.apache.http.pool.AbstractConnPool.shutdown(AbstractConnPool.java:149), 
org.apache.http.impl.conn.PoolingHttpClientConnectionManager.shutdown(PoolingHttpClientConnectionManager.java:430), 
org.apache.http.impl.client.HttpClientBuilder$2.close(HttpClientBuilder.java:1248), 
org.apache.http.impl.client.InternalHttpClient.close(InternalHttpClient.java:201), 
zxf.perf.app.control.TestController.testHttpClient(TestController.java:72), 
zxf.perf.app.control.TestController.newHttpClientDefault(TestController.java:50), 
java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103), 
java.base/java.lang.reflect.Method.invoke(Method.java:580), 
org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205), 
org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:150), 
org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117), 
org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:895), 
org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:808), 
org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87), 
org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1072), 
org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:965), 
org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1006), 
org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:898), 
javax.servlet.http.HttpServlet.service(HttpServlet.java:529), 
org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:883), 
javax.servlet.http.HttpServlet.service(HttpServlet.java:623), 
org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:209), 
org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153), 
org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:51), 
org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178), 
org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153), 
org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100), 
org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117), 
org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178), 
org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153), 
org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93), 
org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117), 
org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178), 
org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153), 
org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201), 
org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117), 
org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178), 
org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153), 
org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:168), 
org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:90), 
org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:481), 
org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:130), 
org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:93), 
org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:74), 
org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:342), 
org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:390), 
org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:63), 
org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:928), 
org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1794), 
org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:52), 
org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1191), 
org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:659), 
org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:61), 
java.base/java.lang.Thread.run(Thread.java:1570)
```

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Apache HttpClient 性能测试和资源泄漏分析工具，对比 HttpClient 4.x 和 5.x 在 Spring Boot 中的资源管理行为。纯分析/演示项目，无单元测试。

## 常用命令

```bash
# 构建整个项目（Java 21 required）
mvn clean package -DskipTests

# 构建单个模块（-am 包含依赖模块）
mvn clean package -pl zxf-springboot-perf-http5 -am
mvn clean package -pl zxf-springboot-perf-http4 -am

# 运行应用（必须先启动 mock 服务，http4 和 http5 共用 8080 端口不能同时运行）
java -jar zxf-springboot-perf-mock/target/*.jar    # 端口 8089
java -jar zxf-springboot-perf-http4/target/*.jar   # 端口 8080
java -jar zxf-springboot-perf-http5/target/*.jar   # 端口 8080

# 压力测试（-c 并发数 -n 总请求数）
ab -c 10 -n 1000000 http://localhost:8080/template/new/default
ab -c 10 -n 1000000 http://localhost:8080/template/new/custom/pool
ab -c 10 -n 1000000 http://localhost:8080/httpclient/new/default?close=false
```

## 项目架构

```
zxf-springboot-perf-monitor    # 资源泄漏监控核心库（无Spring依赖，纯Java）
zxf-springboot-perf-http4      # HttpClient 4.5.14 + Spring Boot 2.7.18
zxf-springboot-perf-http5      # HttpClient 5.5.1 + Spring Boot 3.5.8
zxf-springboot-perf-mock       # 模拟后端服务（支持 ?delay=N 延迟）
```

每个 HTTP 模块的代码结构一致：
- `PerfApplication` - Spring Boot 启动类
- `app/control/TestController` - 测试端点（创建 HttpClient/RestTemplate 并发请求）
- `app/service/WebClientFactory` - 创建各种配置的 HttpClient 和 RestTemplate
- `app/http4|http5/HttpClientMonitor` - 通过反射集成四层监控系统

## 四层监控系统 (monitor 模块)

### 1. ObjectMonitor - 对象生命周期追踪
- 使用 `WeakReference` + `ReferenceQueue` 追踪 Closeable 对象
- 三个后台守护线程：Cleanup(100ms轮询ReferenceQueue)、LeakDetect(30s检查对象年龄)、Stats(60s输出统计)
- TReference 状态机：`ACTIVE → LEAK_SUSPECTED → LEAK_CONFIRMED`，同时任何状态可转为 `GARBAGE_COLLECTED`
- 泄漏判定：对象存活超过 `maxObjectAge`(默认1h) 标记为疑似，疑似数量超过 `leakSuspectThreshold`(默认1000) 确认泄漏

### 2. ThreadMonitor - 线程泄漏检测
- 通过 `ThreadMXBean.dumpAllThreads()` 按关键字过滤 HTTP 相关线程
- 匹配线程数超过阈值时报警

### 3. ClassMonitor - 类实例膨胀检测
- 通过 `JCmdInvoker` 调用 `jcmd <pid> GC.class_histogram`，正则解析实例数
- 监控 HttpClient 相关包名下的类实例数量

### 4. DescriptorMonitor - 文件描述符监控
- 使用 `UnixOperatingSystemMXBean` (仅Unix)
- 可选详细模式：读取 `/proc/self/fd/` 分类 FD 类型（SOCKET/FILE/PIPE/ANON_INODE/DEVICE）

## 监控器集成（关键反射模式）

HttpClientMonitor 通过反射访问 `InternalHttpClient.closeables` 私有字段，提取内部 Closeable 对象注册到 ObjectMonitor：

```java
Field field = httpClient.getClass().getDeclaredField("closeables");
field.setAccessible(true);
// HTTP4: List<Closeable>, HTTP5: ConcurrentLinkedQueue<Closeable>
```

被追踪的 Closeable 类型：`IdleConnectionEvictor`、`PoolingHttpClientConnectionManager`、`BasicHttpClientConnectionManager`

## HTTP4 与 HTTP5 差异

| 方面 | HTTP4 | HTTP5 |
|------|-------|-------|
| Spring Boot | 2.7.18 | 3.5.8 |
| HttpClient 包名 | `org.apache.http` | `org.apache.hc.client5` |
| Closeables 容器 | `List<Closeable>`（遍历关闭） | `ConcurrentLinkedQueue<Closeable>`（poll关闭） |
| 空闲连接驱逐线程名 | `Connection evictor` | `idle-connection-evictor` |
| 响应状态获取 | `response.getStatusLine()` | `response.getCode()` |
| 超时 API | 毫秒整数 | `Timeout`/`TimeValue` 对象 |
| ConnectionManager 构建 | 直接 new | Builder 模式 |
| 监控器类路径 | `zxf.perf.app.http4.HttpClientMonitor` | `zxf.perf.app.http5.HttpClientMonitor` |

## 资源泄漏识别关键字

判断一个类是否有资源泄漏风险，查找以下标识：
- 实现接口：`DisposableBean` / `Closeable`
- 关键字：`finalize` / `destroy` / `close` / `shutdown` / `interrupt` / `Thread` / `Executor`

## 测试端点

- `/template/new/default` - 每次请求创建新的默认 RestTemplate（BasicHttpClientConnectionManager）
- `/template/new/custom/pool` - 每次请求创建自定义连接池 RestTemplate（PoolingHttpClientConnectionManager, 30分钟TTL）
- `/httpclient/new/default` - 每次请求创建原始 HttpClient（支持 `?close=true/false` 和 `?delay=N`）

Mock 服务端点（端口 8089）：
- `/text` - 返回文本资源（163.txt）
- `/binary` - 返回二进制资源（163.dat）
- 支持 `?delay=N` 参数添加延迟（秒）

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Apache HttpClient 性能测试和资源泄漏分析工具，对比 HttpClient 4.x 和 5.x 在 Spring Boot 中的资源管理行为。

## 常用命令

```bash
# 构建整个项目
mvn clean package -DskipTests

# 构建单个模块
mvn clean package -pl zxf-springboot-perf-http5 -am

# 运行应用（需先启动 mock 服务）
java -jar zxf-springboot-perf-mock/target/*.jar    # 端口 8089
java -jar zxf-springboot-perf-http4/target/*.jar   # 端口 8080
java -jar zxf-springboot-perf-http5/target/*.jar   # 端口 8080

# 压力测试
ab -c 10 -n 1000000 http://localhost:8080/template/new/default
ab -c 10 -n 1000000 http://localhost:8080/template/new/custom/pool
ab -c 10 -n 1000000 http://localhost:8080/httpclient/new/default?close=false
```

## 项目架构

```
zxf-springboot-perf-monitor    # 资源泄漏监控核心库（无Spring依赖）
zxf-springboot-perf-http4      # HttpClient 4.5.14 + Spring Boot 2.7.18
zxf-springboot-perf-http5      # HttpClient 5.5.1 + Spring Boot 3.5.8
zxf-springboot-perf-mock       # 模拟后端服务（支持 ?delay=N 延迟）
```

## 四层监控系统 (monitor 模块)

### 1. ObjectMonitor - 对象生命周期追踪
- 使用 `WeakReference` + `ReferenceQueue` 追踪 Closeable 对象
- 三个后台线程：Cleanup(100ms)、LeakDetect(30s)、Stats(60s)
- 核心组件：
  - `TReference` - 包装被追踪对象，存储元数据和生命周期状态
  - `MonitorListener` - 事件回调接口（注册、回收、泄漏嫌疑、确认泄漏、统计更新）
  - `MonitorConfig` - 配置参数（检查间隔、GC触发、阈值等）
  - `MonitorStats` - 统计数据收集

### 2. ThreadMonitor - 线程泄漏检测
- 通过 `ThreadMXBean.dumpAllThreads()` 获取所有线程
- 按搜索关键字过滤 HTTP 相关线程
- 当匹配线程数超过阈值时报警

### 3. ClassMonitor - 类实例膨胀检测
- 使用 `JCmdInvoker` 调用 `jcmd <pid> GC.class_histogram`
- 通过正则解析类名、实例数、字节数
- 监控特定包名下的类实例数量

### 4. DescriptorMonitor - 文件描述符监控
- 使用 `UnixOperatingSystemMXBean` (仅Unix)
- 当打开的文件描述符数超过阈值时报警

## HTTP4 与 HTTP5 差异

| 方面 | HTTP4 | HTTP5 |
|------|-------|-------|
| Spring Boot | 2.7.18 | 3.5.8 |
| HttpClient 包名 | `org.apache.http` | `org.apache.hc.client5` |
| Closeables 容器 | `List<Closeable>` | `ConcurrentLinkedQueue<Closeable>` |
| 线程名 | `Connection evictor` | `idle-connection-evictor` |
| 监控器类路径 | `zxf.perf.app.http4.HttpClientMonitor` | `zxf.perf.app.http5.HttpClientMonitor` |

## 监控器集成

各应用通过 `HttpClientMonitor` 组件集成监控系统：

```java
// 在 RestTemplateFactory 中使用
monitor.monitor(requestFactory.getHttpClient());
```

监控器通过反射访问 `InternalHttpClient.closeables` 字段，将内部的 Closeable 对象注册到 ObjectMonitor 进行追踪。

## 测试端点

- `/template/new/default` - 默认 RestTemplate（每次请求创建新实例）
- `/template/new/custom/pool` - 自定义连接池 RestTemplate（30分钟TTL）
- `/httpclient/new/default` - 原始 HttpClient（支持 `?close=true/false` 控制资源释放）

Mock 服务端点（端口 8089）：
- `/text` - 返回文本资源（163.txt）
- `/binary` - 返回二进制资源（163.dat）
- 支持 `?delay=N` 参数添加延迟（秒）
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

1. **ObjectMonitor** - WeakReference 追踪 Closeable 对象生命周期
2. **ThreadMonitor** - 检查 HTTP 相关线程（如 `idle-connection-evictor`）
3. **ClassMonitor** - 通过 `jcmd GC.class_histogram` 检测类实例膨胀
4. **DescriptorMonitor** - UnixOperatingSystemMXBean 监控文件描述符

## HTTP4 与 HTTP5 差异

| 方面 | HTTP4 | HTTP5 |
|------|-------|-------|
| Spring Boot | 2.7.18 | 3.5.8 |
| Closeables 容器 | `List<Closeable>` | `ConcurrentLinkedQueue<Closeable>` |
| 线程名 | `Connection evictor` | `idle-connection-evictor` |

## 测试端点

- `/template/new/default` - 默认 RestTemplate
- `/template/new/custom/pool` - 自定义连接池 RestTemplate（30分钟TTL）
- `/httpclient/new/default` - 原始 HttpClient（支持 `?close=true/false`）
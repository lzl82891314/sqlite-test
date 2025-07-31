# SQLite NFS多进程并发测试工程

此工程专门用于测试SQLite数据库在NFS环境下的多进程并发读写问题，主要用于检测SQLite文件在网络文件系统中可能出现的损坏问题。

## 功能特性

- 真正的多进程（非多线程）并发测试
- 基于JdbcTemplate的SQLite数据访问
- 专门针对NFS环境的文件损坏检测
- 自动数据库初始化和测试数据生成
- 完整的异常监控和日志记录
- 可打包成独立jar文件，方便在不同环境部署

## 测试目标

1. **检测SQLite在NFS环境下的并发写入问题**
2. **模拟真实的多进程并发访问场景**
3. **快速发现数据库文件损坏问题**
4. **提供详细的错误日志和诊断信息**

## 项目结构

```
src/main/java/com/grapecity/phoenix/sqlitetest/
├── MainProcess.java          # 主进程：协调器和监控器
├── ChildProcess.java         # 子进程：独立的并发测试进程
├── DatabaseConfig.java      # SQLite数据库连接配置
├── UserRepository.java      # Users表操作和数据访问
└── NfsTestException.java    # 自定义异常（检测数据库损坏）

src/main/resources/
├── application.properties   # 配置参数
└── logback.xml             # 日志配置
```

## 快速开始

### 1. 编译打包

```bash
mvn clean package
```

编译完成后会在`target/`目录生成`sqlite-nfs-test-1.0-SNAPSHOT.jar`文件。

### 2. 运行测试

**默认测试（4个子进程，100个周期）：**
```bash
java -jar target/sqlite-nfs-test-1.0-SNAPSHOT.jar
```

**指定子进程数量：**
```bash
# 启动8个子进程
java -jar target/sqlite-nfs-test-1.0-SNAPSHOT.jar 8

# 启动4个子进程，使用自定义数据库路径
java -jar target/sqlite-nfs-test-1.0-SNAPSHOT.jar 4 /mnt/nfs/test.db

# 启动4个子进程，自定义路径，200个周期
java -jar target/sqlite-nfs-test-1.0-SNAPSHOT.jar 4 /mnt/nfs/test.db 200
```

**持续测试模式：**
```bash
# 无限循环模式（直到手动停止或出现错误）
java -jar target/sqlite-nfs-test-1.0-SNAPSHOT.jar 4 /mnt/nfs/test.db -1

# 按时间运行：4个进程运行10分钟
java -jar target/sqlite-nfs-test-1.0-SNAPSHOT.jar 4 /mnt/nfs/test.db 0 600

# 按时间运行：8个进程运行1小时
java -jar target/sqlite-nfs-test-1.0-SNAPSHOT.jar 8 /mnt/nfs/test.db 0 3600
```

**显示帮助信息：**
```bash
java -jar target/sqlite-nfs-test-1.0-SNAPSHOT.jar --help
```

## 测试工作流程

### 主进程工作流程
1. **初始化数据库**：创建SQLite文件和users表
2. **插入测试数据**：随机生成20个用户记录
3. **启动子进程**：根据参数启动多个子进程
4. **监控异常**：实时监控子进程状态和输出
5. **检测损坏**：识别数据库损坏异常并记录
6. **生成报告**：输出最终测试结果

### 子进程工作流程
每个子进程独立执行以下循环：
1. **随机查询**：对users表执行随机条件查询
2. **批量插入**：持续1秒钟插入新用户数据（5-10ms间隔，高并发压力）
3. **提交事务**：确保数据持久化
4. **等待间隔**：休眠0.5秒后进入下一个周期（增加并发频率）
5. **异常检测**：监控数据库损坏并立即退出

## 关键技术特性

### SQLite配置
- **busy_timeout=5000**：5秒忙等待，减少SQLITE_BUSY错误
- **journal_mode=WAL**：启用WAL模式，支持读写并发操作
- **synchronous=NORMAL**：平衡数据安全性和性能
- **cache_size=10000**：设置页面缓存为10000页，提高并发性能
- **自动提交事务**：使用JdbcTemplate默认事务模式
- **文件损坏检测**：自动识别常见的SQLite损坏错误

### 异常处理策略
- **子进程异常**：检测到损坏立即退出（退出码2）
- **主进程监控**：实时收集子进程输出和异常
- **强制终止**：任何进程异常时终止所有进程
- **详细日志**：记录完整的错误堆栈和诊断信息

### 日志系统
- **主进程日志**：`logs/nfs-test-main.log`
- **子进程日志**：`logs/nfs-test-child.log`  
- **错误日志**：`logs/nfs-test-errors.log`
- **控制台输出**：实时显示测试进度

### 实时监控
- **进度报告**：每30秒输出统计信息
- **插入速率**：显示平均插入速度
- **剩余时间**：按时间模式下显示剩余时间
- **总记录数**：实时显示数据库中的记录总数

## NFS测试建议

### 测试环境准备
1. 在NFS挂载点创建测试目录
2. 确保多个客户端可以访问同一路径
3. 配置合适的NFS挂载选项

### 测试场景
```bash
# 在NFS挂载点运行测试
java -jar sqlite-nfs-test.jar 4 /mnt/nfs/share/test.db 50

# 同时在多个客户端运行
# 客户端1
java -jar sqlite-nfs-test.jar 2 /mnt/nfs/share/db1.db 30

# 客户端2  
java -jar sqlite-nfs-test.jar 2 /mnt/nfs/share/db2.db 30
```

### 常见问题检测
- **文件锁冲突**：SQLITE_BUSY错误
- **数据损坏**：`database disk image is malformed`
- **网络中断**：`file is not a database`
- **并发写入**：数据不一致或丢失

## 输出结果解释

### 成功输出示例
```
2025-01-31 10:00:00.123 [main] INFO MainProcess - Starting SQLite NFS multi-process test
2025-01-31 10:00:00.456 [main] INFO MainProcess - Database initialized successfully with 20 users
2025-01-31 10:00:00.789 [main] INFO MainProcess - Started child process: ChildProcess-1
2025-01-31 10:00:00.890 [main] INFO MainProcess - Started child process: ChildProcess-2
...
2025-01-31 10:01:30.123 [main] INFO MainProcess - All child processes completed successfully
2025-01-31 10:01:30.456 [main] INFO MainProcess - Final user count: 1250
2025-01-31 10:01:30.789 [main] INFO MainProcess - Database integrity check: PASSED
```

### 失败输出示例
```
2025-01-31 10:00:15.123 [ChildProcess-1] ERROR ChildProcess - Database corruption detected
2025-01-31 10:00:15.456 [main] ERROR MainProcess - Child process ChildProcess-1 detected database corruption (exit code: 2)
2025-01-31 10:00:15.789 [main] ERROR MainProcess - Database corruption detected by child processes
```

## 命令行参数

| 参数位置 | 说明 | 默认值 | 示例 |
|---------|------|--------|------|
| 1 | 子进程数量 | 4 | `java -jar test.jar 8` |
| 2 | 数据库文件路径 | nfs-test.db | `java -jar test.jar 4 /mnt/nfs/test.db` |
| 3 | 每个子进程的最大周期数 | 100 | `java -jar test.jar 4 /path/db 200` |
| 3 (特殊) | -1表示无限循环 | - | `java -jar test.jar 4 /path/db -1` |
| 3 + 4 | 0 + 运行时间(秒) | - | `java -jar test.jar 4 /path/db 0 600` |

### 运行模式

1. **固定周期模式**：`java -jar test.jar 4 /path/db 100`
2. **无限循环模式**：`java -jar test.jar 4 /path/db -1`
3. **按时间运行模式**：`java -jar test.jar 4 /path/db 0 600`

## 系统要求

- **Java 17+**
- **SQLite JDBC驱动**（已包含在jar中）
- **Spring Framework**（已包含在jar中）
- **网络文件系统访问权限**

## 故障排查

### 常见错误及解决方案

1. **权限错误**
   ```
   解决：确保对NFS挂载点有读写权限
   chmod 755 /mnt/nfs/
   ```

2. **网络问题**  
   ```
   解决：检查NFS连接稳定性
   mount | grep nfs
   ```

3. **Java版本**
   ```
   解决：确保使用Java 17或更高版本
   java -version
   ```

这个工具专门设计用于快速发现SQLite在NFS环境下的并发问题，帮助您验证网络文件系统的可靠性。
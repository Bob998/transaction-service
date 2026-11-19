# JVM与Linux诊断命令

一次采集JVM快照：

```bash
./diagnostics/capture-jvm.sh
```

输出保存在被Git忽略的 `diagnostics/output/`。

常用命令：

```bash
# JVM启动参数、堆概况和GC原因
docker exec transaction-app jcmd 1 VM.flags
docker exec transaction-app jcmd 1 GC.heap_info
docker exec transaction-app jcmd 1 VM.native_memory summary

# 线程栈和Java对象直方图
docker exec transaction-app jstack 1
docker exec transaction-app jmap -histo:live 1 | head -50

# 容器CPU、内存与Java线程
docker stats transaction-app
docker exec transaction-app top -H -b -n 1
docker exec transaction-app ps -eLo pid,tid,pcpu,pmem,stat,comm

# 数据库活动连接与慢查询
docker exec transaction-postgres psql \
  -U transaction_user -d transaction_db \
  -c "SELECT pid, state, wait_event_type, wait_event, now()-query_start AS duration, query FROM pg_stat_activity WHERE datname='transaction_db' ORDER BY query_start;"
```

只有在确实需要、并确认磁盘空间足够时才生成堆转储：

```bash
docker exec transaction-app jcmd 1 GC.heap_dump /tmp/week10.hprof
docker cp transaction-app:/tmp/week10.hprof diagnostics/output/
```

堆转储可能触发Stop-The-World并占用大量磁盘，生产环境必须先评估影响。

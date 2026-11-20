# 第12周最终交付

## 交付清单

- [架构图](ARCHITECTURE.md)
- [性能优化报告](PERFORMANCE_REPORT.md)（详细实验见 [第10周](WEEK10_PERFORMANCE_TUNING.md)）
- [Kafka Kubernetes事故复盘](INCIDENT_POSTMORTEM.md)
- [系统演示脚本](DEMO_SCRIPT.md) 与 `scripts/demo-system.sh`
- [简历项目描述](RESUME_PROJECT.md)
- [中英文项目介绍](PROJECT_INTRO_CN_EN.md)
- [面试问题库](INTERVIEW_QUESTIONS.md)
- [完整系统设计](WEEK7_SYSTEM_DESIGN.md)

## 遗留问题收口

- 健康详情默认改为不公开，Compose/Kubernetes实验环境通过变量显式开启。
- 演示流程变成可重复执行脚本，幂等键每次自动生成。
- 性能结论明确标注为本地实验数据，不冒充生产指标。
- 文档按架构、性能、事故、演示和求职材料拆分，并从README统一导航。

## AI模块决策

本轮暂不加入RAG。当前项目已经覆盖交易正确性、可靠消息、路由风控、对账、测试、CI/CD、可观测性、性能和Kubernetes。先保证这些能力可运行、可解释、可举证，价值高于加入一个半成品AI模块。

未来满足以下条件再开始AI模块：核心质量门禁持续通过；演示可重复；制度文档来源和授权明确；能够实现引用溯源、只读工具、人工审批、审计与评估集，而不是只做向量检索Demo。

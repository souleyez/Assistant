# Gemma 4 YOLO Studio

`Gemma 4 YOLO Studio` 是一个纯单机版训练平台：

- 前端：Vue 3 + Vite
- 后端：Java 8 + Spring Boot 2.7
- 运行模式：单机工作站，本地数据、本地训练、本地模型产物

当前首版聚焦这 6 条主线：

- 总览
- 数据集管理
- 训练项目管理
- 训练任务队列
- 模型仓库
- Gemma 4 助手建议位

当前版本不做这些事情：

- 不和其他项目做联动
- 不接入多机调度或集群
- 不引入对象存储或外部任务编排
- 不保留旧的文档/报表业务语义

## 目录

```text
frontend/  Vue 3 单页应用
backend/   Spring Boot API
deploy/    Linux 单机部署脚本
```

## 本地启动

### 前端

```powershell
cd frontend
npm install
npm run dev
```

默认地址：`http://127.0.0.1:5173`

### 后端

```powershell
cd backend
mvn spring-boot:run
```

默认地址：`http://127.0.0.1:8080`

如果本机没有 Maven，请先安装 Maven 3.9+，或者直接用 IntelliJ IDEA / VS Code Java 插件导入 `backend/pom.xml` 后运行。

## 当前数据模型

- `datasets`
  - 本地 YOLO 数据集目录、样本数、类别数、版本、标注格式
- `projects`
  - 训练规格，包括数据集绑定、YOLO 版本、超参数、实验目标
- `jobs`
  - 单机训练任务、进度、指标、输出目录、日志、权重目录
- `models`
  - 训练产物或手工登记模型
- `gemmaConversations`
  - Gemma 4 助手建议记录

## 说明

- 当前后端使用本地 JSON 文件做轻量持久化
- Gemma 4 当前先以平台内建议引擎占位，便于后续接真实本地推理服务
- 训练任务现在会生成本地运行目录、数据集 yaml、训练脚本和日志文件
- 默认通过 `python` 启动训练；如果你要指定解释器，可设置环境变量 `YOLO_PYTHON`

## 本机训练前提

建议先准备一个单独的 Python 环境，并安装：

```powershell
pip install ultralytics
```

如果 `python` 不是你想用的解释器，可以在启动后端前指定：

```powershell
$env:YOLO_PYTHON = 'D:\miniconda3\envs\gemma4-yolo\python.exe'
```

训练任务启动后，平台会在本地生成：

- 运行目录
- `dataset.yaml`
- PowerShell 启动脚本
- Python 训练脚本
- `train.log`

## Linux 单机部署

如果你要在 Linux 服务器上长期跑这个平台，直接用仓库里的脚本：

```bash
cd /home/xigma01/apps/Assistant
bash deploy/linux/setup-server.sh
```

这套脚本会做这些事情：

- 在项目根目录创建 `.venv`，并安装 `ultralytics`
- 构建前端 `dist`
- 打包后端 jar
- 以用户态后台进程启动前后端
- 安装 `crontab @reboot`，服务器重启后自动拉起

常用运维命令：

```bash
bash deploy/linux/status.sh
bash deploy/linux/restart.sh
bash deploy/linux/stop.sh
```

默认端口：

- 前端：`4173`
- 后端：`8080`

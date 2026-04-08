# Assistant

`Assistant` 是从 `ai-data-platform` 主应用面向新技术栈重建的仓库：

- 前端：Vue 3 + Vite
- 后端：Java 8 + Spring Boot 2.7

当前首版迁移范围聚焦原仓库的主业务应用：

- 工作台首页
- 文档中心
- 数据源工作台
- 报表中心

暂未纳入首版：

- Windows 安装器与客户端发布链
- 原 `worker` 进程
- 冻结中的 control-plane 目录

## 目录

```text
frontend/  Vue 3 单页应用
backend/   Spring Boot API
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

## 迁移说明

- 原 Next.js 页面被重组为 Vue Router 页面
- 原 Fastify API 被收敛为 Spring Boot REST 控制器
- 首版后端使用本地 JSON 文件做轻量持久化，便于快速接手和后续替换成数据库
- 接口命名尽量保留原项目语义，降低迁移成本

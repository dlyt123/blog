# 个人二次元博客网站

基于 **Vue 3 + Spring Boot + MySQL** 的前后端分离个人博客，二次元画风。

## 项目结构

```
demo/
├── sql/sql.txt              # 数据库初始化脚本（建表 + 示例数据）
├── back-ed/backed-demo/     # 后端（Spring Boot 4 + MyBatis）
│   └── src/main/
│       ├── java/com/back/backeddemo/
│       │   ├── common/      # 统一响应、异常处理
│       │   ├── config/      # JWT、拦截器、初始化
│       │   ├── entity/      # 实体类
│       │   ├── mapper/      # Mapper 接口
│       │   ├── service/     # 业务逻辑
│       │   └── controller/  # 控制器
│       └── resources/
│           ├── application.yml   # 数据源 + MyBatis 配置
│           └── mapper/*.xml      # MyBatis SQL 映射
└── front/front-demo/        # 前端（Vue 3 + Vite）
    └── src/
        ├── api/             # Axios 封装 + 接口
        ├── router/          # 路由
        ├── store/           # Pinia
        ├── components/      # 组件
        ├── views/           # 页面（前台 + 后台 admin）
        ├── styles/anime.css # 二次元风格
        └── assets/          # 二次元图片
```

## 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17+（推荐 21） |
| Maven | 3.6+ |
| Node.js | 20+ |
| MySQL | 8.0 |

## 启动步骤

### 1. 初始化数据库

用 MySQL 客户端执行 `sql/sql.txt`，会自动创建 `blog` 数据库、建表并插入示例数据。

### 2. 启动后端

1. 打开 `back-ed/backed-demo/src/main/resources/application.yml`
2. 修改 `spring.datasource.username` 和 `password` 为你本机 MySQL 的账号密码
3. 执行：
   ```bash
   cd back-ed/backed-demo
   mvn spring-boot:run
   ```
4. 后端启动在 `http://localhost:8080`，首次启动会自动创建管理员账号

### 3. 启动前端

```bash
cd front/front-demo
npm install
npm run dev
```

前端启动在 `http://localhost:5173`，已配置代理把 `/api` 和 `/uploads` 转发到后端 8080。

## 默认账号

- 用户名：`admin`
- 密码：`admin123`

登录入口：前端右上角「登录」，或直接访问 `http://localhost:5173/login`。

## 功能清单

- **前台**：首页文章列表、文章详情（Markdown 渲染 + 目录）、分类、标签、归档时间线、全文搜索、关于我、友链
- **互动**：评论（楼中楼 + 审核）、点赞、收藏
- **后台**：文章管理（发布/草稿/置顶/推荐/批量）、分类/标签管理、评论审核、友链管理、站点设置、数据统计
- **扩展**：RSS 订阅、站点地图、邮件订阅
- **安全**：JWT 鉴权、BCrypt 密码加密、全局异常处理

## 二次元图片说明

网站 hero 横幅和文章默认封面使用了 AI 生成的二次元插画（位于 `front/front-demo/src/assets/`），可在对应 Vue 组件中替换为你喜欢的图片。

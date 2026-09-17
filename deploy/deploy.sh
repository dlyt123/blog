#!/usr/bin/env bash
# ================================================================
# 个人博客网站 · 一键部署脚本（在服务器上执行）
#
# 用法：
#   sudo ./deploy.sh --jar ./blog.jar --dist ./dist --domain example.cn
#
# 参数都可以省略，默认值见下方 CONFIG 区。
#
# 脚本做的事：
#   1) 建目录、建运行用户
#   2) 备份旧版本 → 上传新 jar 与前端 dist
#   3) 安装 systemd 服务与 Nginx 站点配置
#   4) 重启服务并做健康检查
#
# 幂等：可重复执行，每次都会先备份旧 jar。
# 注意：**不会**覆盖已存在的 systemd / Nginx 配置（避免冲掉你填的密钥），
#       需要更新配置时请手动改，或先删除旧文件再跑。
# ================================================================

set -euo pipefail

# ---------------- CONFIG（可按需修改） ----------------
APP_DIR="/opt/blog/app"
UPLOAD_DIR="/opt/blog/uploads"
LOG_DIR="/opt/blog/logs"
BACKUP_DIR="/opt/blog/backup"
WEB_DIR="/var/www/blog"
RUN_USER="blog"
BACKEND_PORT="8080"

JAR_SRC="./blog.jar"
DIST_SRC="./dist"
DOMAIN="example.cn"

# ---------------- 解析参数 ----------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --jar)    JAR_SRC="$2"; shift 2 ;;
    --dist)   DIST_SRC="$2"; shift 2 ;;
    --domain) DOMAIN="$2"; shift 2 ;;
    -h|--help)
      grep '^#' "$0" | sed 's/^# \{0,1\}//' | head -20
      exit 0 ;;
    *) echo "未知参数: $1"; exit 1 ;;
  esac
done

log()  { echo -e "\033[36m[部署]\033[0m $*"; }
warn() { echo -e "\033[33m[警告]\033[0m $*"; }
die()  { echo -e "\033[31m[失败]\033[0m $*" >&2; exit 1; }

# ---------------- 0. 前置检查 ----------------
[[ $EUID -eq 0 ]] || die "请用 root 执行：sudo $0 ..."
[[ -f "$JAR_SRC" ]] || die "找不到后端 jar：$JAR_SRC（先执行 mvn clean package -DskipTests）"
[[ -d "$DIST_SRC" ]] || die "找不到前端构建产物目录：$DIST_SRC（先执行 npm run build）"
command -v java >/dev/null || die "服务器未安装 Java，请先装 JDK 21"
java -version 2>&1 | grep -q '"21' || warn "当前 Java 不是 21，项目要求 JDK 21，请确认"
command -v nginx >/dev/null || warn "未检测到 Nginx，第 5 步会跳过（请自行安装）"

log "后端 jar : $JAR_SRC"
log "前端产物 : $DIST_SRC"
log "域名     : $DOMAIN"

# ---------------- 1. 目录与用户 ----------------
log "创建目录与运行用户..."
id -u "$RUN_USER" >/dev/null 2>&1 || useradd -m -s /bin/bash "$RUN_USER"
mkdir -p "$APP_DIR" "$UPLOAD_DIR" "$LOG_DIR" "$BACKUP_DIR" "$WEB_DIR"

# ---------------- 2. 备份旧版本 ----------------
if [[ -f "$APP_DIR/blog.jar" ]]; then
  STAMP=$(date +%Y%m%d%H%M%S)
  cp "$APP_DIR/blog.jar" "$BACKUP_DIR/blog.jar.$STAMP"
  log "已备份旧 jar → $BACKUP_DIR/blog.jar.$STAMP"
fi
if [[ -d "$WEB_DIR" && -n "$(ls -A "$WEB_DIR" 2>/dev/null)" ]]; then
  STAMP=$(date +%Y%m%d%H%M%S)
  tar -czf "$BACKUP_DIR/dist.$STAMP.tar.gz" -C "$WEB_DIR" . 2>/dev/null || true
  log "已备份旧前端 → $BACKUP_DIR/dist.$STAMP.tar.gz"
fi

# ---------------- 3. 部署文件 ----------------
log "部署后端 jar..."
cp "$JAR_SRC" "$APP_DIR/blog.jar"

log "部署前端静态文件..."
rm -rf "${WEB_DIR:?}"/*
cp -r "$DIST_SRC"/. "$WEB_DIR"/

chown -R "$RUN_USER:$RUN_USER" /opt/blog "$WEB_DIR"
chmod 750 "$UPLOAD_DIR" "$LOG_DIR"

# ---------------- 4. 数据库提示 ----------------
log "检查数据库与管理员账号..."
if command -v mysql >/dev/null && [[ -f "$APP_DIR/application-local.yml" ]]; then
  log "检测到 $APP_DIR/application-local.yml，数据库配置走该文件"
else
  warn "未发现 $APP_DIR/application-local.yml —— 请确认已通过 systemd 环境变量注入数据库密码"
fi

# ---------------- 5. systemd 服务 ----------------
SERVICE_FILE="/etc/systemd/system/blog.service"
if [[ -f "$SERVICE_FILE" ]]; then
  warn "systemd 配置已存在，跳过覆盖（避免冲掉你填的 JWT 密钥/数据库密码）"
  warn "如需更新：先备份并编辑 $SERVICE_FILE，再执行 systemctl daemon-reload"
else
  if [[ -f "$(dirname "$0")/blog.service" ]]; then
    cp "$(dirname "$0")/blog.service" "$SERVICE_FILE"
    log "已安装 systemd 服务 → $SERVICE_FILE"
    warn "请先编辑 $SERVICE_FILE，把 BLOG_JWT_SECRET / DB_PASSWORD 换成真实值！"
  else
    warn "未找到 blog.service，跳过 systemd 安装"
  fi
fi

systemctl daemon-reload
systemctl enable blog >/dev/null 2>&1 || true

# ---------------- 6. Nginx 配置 ----------------
NGINX_FILE="/etc/nginx/sites-available/blog"
if command -v nginx >/dev/null; then
  if [[ -f "$NGINX_FILE" ]]; then
    warn "Nginx 站点配置已存在，跳过覆盖：$NGINX_FILE"
  else
    if [[ -f "$(dirname "$0")/nginx-blog.conf" ]]; then
      # 把示例域名替换成传入的域名
      sed "s/example\.cn/$DOMAIN/g" "$(dirname "$0")/nginx-blog.conf" > "$NGINX_FILE"
      mkdir -p /etc/nginx/sites-enabled
      ln -sf "$NGINX_FILE" /etc/nginx/sites-enabled/blog
      [[ -f /etc/nginx/sites-enabled/default ]] && rm -f /etc/nginx/sites-enabled/default
      log "已安装 Nginx 站点配置 → $NGINX_FILE（域名已替换为 $DOMAIN）"
    else
      warn "未找到 nginx-blog.conf，跳过 Nginx 配置"
    fi
  fi
  nginx -t && systemctl reload nginx && log "Nginx 配置校验通过并已重载"
fi

# ---------------- 7. 重启后端并健康检查 ----------------
log "重启后端服务..."
systemctl restart blog
sleep 8

if curl -sf -m 5 "http://127.0.0.1:${BACKEND_PORT}/api/site-info" >/dev/null; then
  echo ""
  echo -e "\033[32m========================================\033[0m"
  echo -e "\033[32m  ✅ 部署完成，后端健康检查通过\033[0m"
  echo -e "\033[32m========================================\033[0m"
  echo ""
  echo "接下来还需要手动做的事："
  echo "  1) 检查启动日志：  journalctl -u blog -n 50"
  echo "     ★ 若出现「JWT 密钥还是占位符」告警，说明 $SERVICE_FILE 里的密钥没填"
  echo "  2) 建管理员账号：  mysql -uroot -p < tools/create-admin.sql"
  echo "  3) 申请证书并按部署文档第 7 章切到 HTTPS"
  echo "  4) 逐项走《部署文档》第 8 章「上线检查清单」"
  echo ""
  echo "常用命令："
  echo "  查看状态  systemctl status blog"
  echo "  实时日志  journalctl -u blog -f"
  echo "  应用日志  tail -f ${LOG_DIR}/blog.log"
  echo "  重启后端  systemctl restart blog"
else
  echo ""
  die "健康检查失败！请查看日志排查：journalctl -u blog -n 80"
fi

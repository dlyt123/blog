<script setup>
import { ref, onMounted } from 'vue'
import { getSettings, adminUpdateSettings, uploadMedia, adminExport, adminBackupNow, adminListBackups, adminMailStatus, adminSendTestMail } from '@/api'
import { ElMessage } from 'element-plus'

const form = ref({})
const saving = ref(false)
const uploadingLogo = ref(false)
const logoInput = ref(null)
const backups = ref([])
const backingUp = ref(false)
const mailStatus = ref({ configured: false, from: '' })
const testMailTo = ref('')
const sendingTestMail = ref(false)

onMounted(async () => {
  const data = (await getSettings()) || {}
  // pageSize 在库里是字符串，这里转成数字给 el-input-number 用。
  // police / icp 给个默认空串兜底：键不存在时 v-model 会绑不上（库里还没这行时尤其明显）
  form.value = { icp: '', police: '', ...data, pageSize: Number(data.pageSize) > 0 ? Number(data.pageSize) : 10 }
  loadBackups()
  loadMailStatus()
})

/** 邮件配置状态 */
async function loadMailStatus() {
  try {
    mailStatus.value = (await adminMailStatus()) || { configured: false, from: '' }
  } catch (e) {
    mailStatus.value = { configured: false, from: '' }
  }
}

/** 发送测试邮件（真发一封，立刻知道 SMTP 通不通） */
async function sendTestMail() {
  if (!testMailTo.value || !testMailTo.value.includes('@')) {
    ElMessage.warning('请输入收件邮箱')
    return
  }
  sendingTestMail.value = true
  try {
    await adminSendTestMail(testMailTo.value.trim())
    ElMessage.success('测试邮件已发出，请去收件箱查看（也看看垃圾箱）')
  } catch (e) {
    // 拦截器已提示具体失败原因
  } finally {
    sendingTestMail.value = false
  }
}

/** 备份文件列表 */
async function loadBackups() {
  try {
    backups.value = (await adminListBackups()) || []
  } catch (e) {
    backups.value = []
  }
}

/** 立即备份数据库 */
async function doBackup() {
  backingUp.value = true
  try {
    const r = await adminBackupNow()
    ElMessage.success(`备份完成：${r.file}`)
    loadBackups()
  } catch (e) {
    // 拦截器已提示
  } finally {
    backingUp.value = false
  }
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const kb = bytes / 1024
  if (kb < 1024) return kb.toFixed(1) + ' KB'
  return (kb / 1024).toFixed(2) + ' MB'
}

async function save() {
  saving.value = true
  try {
    await adminUpdateSettings(form.value)
    ElMessage.success('设置已保存')
  } finally {
    saving.value = false
  }
}

/** 站点 Logo：选择本地图片上传 */
function pickLogo() {
  logoInput.value && logoInput.value.click()
}

async function onLogoChange(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    e.target.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    e.target.value = ''
    return
  }
  uploadingLogo.value = true
  try {
    const res = await uploadMedia(file)
    form.value.logo = res.url
    ElMessage.success('Logo 已上传，记得点击「保存设置」')
  } catch (err) {
    // 拦截器已提示
  } finally {
    uploadingLogo.value = false
    e.target.value = ''
  }
}

/** 数据导出：全部文章打包成 Markdown 的 zip 下载 */
async function exportData() {
  try {
    await adminExport()
    ElMessage.success('已开始下载')
  } catch (e) {
    ElMessage.error('导出失败，请稍后重试')
  }
}
</script>

<template>
  <div>
    <h3 class="title">⚙️ 站点设置</h3>

    <el-form label-width="100px" style="max-width: 640px">
      <el-form-item label="站点名称">
        <el-input v-model="form.siteName" placeholder="显示在顶部导航与首页大标题" />
      </el-form-item>
      <el-form-item label="副标题">
        <el-input v-model="form.slogan" placeholder="显示在页脚" />
      </el-form-item>
      <el-form-item label="站点 Logo">
        <div class="logo-row">
          <div class="logo-preview">
            <img v-if="form.logo" :src="form.logo" alt="logo 预览" />
            <span v-else class="logo-empty">未设置</span>
          </div>
          <div class="logo-actions">
            <input
              ref="logoInput"
              type="file"
              accept="image/*"
              class="hidden-file"
              @change="onLogoChange"
            />
            <el-button :loading="uploadingLogo" @click="pickLogo">
              {{ form.logo ? '重新选择' : '上传图片' }}
            </el-button>
            <el-button v-if="form.logo" @click="form.logo = ''">移除</el-button>
            <span class="tip">不设置则显示默认图标</span>
          </div>
        </div>
      </el-form-item>
      <el-form-item label="站点描述">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="显示在首页副标题" />
      </el-form-item>
      <el-form-item label="每页文章数">
        <el-input-number v-model="form.pageSize" :min="1" :max="50" />
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="form.keywords" placeholder="用逗号分隔" />
      </el-form-item>
      <el-form-item label="ICP 备案号">
        <el-input v-model="form.icp" placeholder="如：渝ICP备2026000000号-1（页脚自动链到工信部）" />
      </el-form-item>
      <el-form-item label="公安备案号">
        <el-input v-model="form.police" placeholder="如：渝公网安备50000000000000号（页脚自动链到公安部查询页）" />
      </el-form-item>
      <el-form-item label="关于我">
        <el-input v-model="form.about" type="textarea" :rows="6" placeholder="支持 Markdown，展示在「关于」页" />
      </el-form-item>
      <el-form-item label="版权声明">
        <el-input
          v-model="form.copyright"
          type="textarea"
          :rows="2"
          placeholder="显示在每篇文章底部，例如：本文采用 CC BY-NC-SA 4.0 许可协议，转载请注明出处。"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
        <el-button @click="exportData">📦 导出文章（Markdown 打包）</el-button>
      </el-form-item>
    </el-form>

    <!-- 邮件配置自检 -->
    <div class="backup-card">
      <div class="backup-head">
        <h4 class="backup-title">📧 邮件通知</h4>
        <el-button link @click="loadMailStatus">刷新状态</el-button>
      </div>
      <p class="backup-tip">
        用于「忘记密码」「评论回复通知」「新文章推送订阅者」。
        SMTP 账号在服务器的 <code>application-local.yml</code> 或环境变量里配置，改完需重启服务。
      </p>
      <p class="mail-status">
        当前状态：
        <span v-if="mailStatus.configured" class="ok-text">已配置</span>
        <span v-else class="bad-text">未配置</span>
        <span v-if="mailStatus.from" class="mail-from">（发件人：{{ mailStatus.from }}）</span>
      </p>
      <div class="mail-test">
        <el-input v-model="testMailTo" placeholder="收件邮箱，例如 you@qq.com" style="width: 260px" />
        <el-button type="primary" :loading="sendingTestMail" @click="sendTestMail">发送测试邮件</el-button>
      </div>
      <p class="backup-tip">
        ⚠️ 测试邮件是<b>真发</b>的，会立刻返回成败原因。国内邮箱偶尔会把通知类邮件丢进垃圾箱，记得也看一眼。
      </p>
    </div>

    <!-- 数据库备份 -->
    <div class="backup-card">
      <div class="backup-head">
        <h4 class="backup-title">💾 数据库备份</h4>
        <div>
          <el-button :loading="backingUp" @click="doBackup">立即备份</el-button>
          <el-button link @click="loadBackups">刷新列表</el-button>
        </div>
      </div>
      <p class="backup-tip">
        每天凌晨 3:00 自动备份，保留最近 7 个。备份文件在服务器 <code>backups/</code> 目录，恢复时用
        <code>mysql -uroot -p &lt; 备份文件.sql</code>。
      </p>
      <el-table v-if="backups.length" :data="backups" size="small">
        <el-table-column prop="file" label="备份文件" min-width="240" />
        <el-table-column label="大小" width="120">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
      </el-table>
      <p v-else class="backup-empty">还没有备份文件，点「立即备份」试试。</p>
    </div>
  </div>
</template>

<style scoped>
.title {
  margin: 0 0 20px;
  color: var(--text-strong);
}

.logo-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.logo-preview {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--border-soft);
  background: var(--surface-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.logo-empty {
  color: var(--text-faint);
  font-size: 11px;
}

.logo-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.logo-actions .tip {
  color: var(--text-muted);
  font-size: 12px;
}

.hidden-file {
  display: none;
}

.backup-card {
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px dashed var(--border-soft);
  max-width: 640px;
}

.backup-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.backup-title {
  margin: 0;
  color: var(--text-strong);
}

.backup-tip {
  margin: 0 0 12px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.7;
}

.backup-tip code {
  background: var(--surface-soft);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12px;
}

.backup-empty {
  color: var(--text-muted);
  font-size: var(--text-sm);
  padding: var(--space-3) 0;
}

/* ===== 邮件配置自检 ===== */
.mail-status {
  margin: 0 0 var(--space-3);
  font-size: var(--text-sm);
  color: var(--text-body);
}

.ok-text { color: var(--success); font-weight: 600; }
.bad-text { color: var(--danger); font-weight: 600; }
.mail-from { color: var(--text-muted); font-size: var(--text-xs); }

.mail-test {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}
</style>

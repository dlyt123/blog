<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useSiteStore } from '@/store/site'

const route = useRoute()
const siteStore = useSiteStore()
const active = ref('privacy')

const TABS = [
  { key: 'agreement', label: '用户协议' },
  { key: 'privacy', label: '隐私政策' },
  { key: 'disclaimer', label: '免责声明' }
]

onMounted(() => {
  const t = route.query.tab
  if (t && TABS.some((x) => x.key === t)) active.value = t
  window.scrollTo({ top: 0 })
})

const updatedAt = '2026 年 09 月'
</script>

<template>
  <div class="policy-wrap">
    <h2 class="page-title anime-title">📋 用户协议与免责声明</h2>
    <p class="subtitle">
      本文档适用于 {{ siteStore.siteName || '本站' }}。你注册、登录、浏览或发布内容前，请先阅读并同意以下条款。
    </p>

    <div class="tabs anime-tabs">
      <button
        v-for="t in TABS"
        :key="t.key"
        class="anime-tab"
        :class="{ on: active === t.key }"
        @click="active = t.key"
      >{{ t.label }}</button>
    </div>

    <div class="anime-card anime-card--flat doc">
      <!-- ============ 用户协议 ============ -->
      <template v-if="active === 'agreement'">
        <h3>一、服务说明</h3>
        <p>
          本站是一个面向公众开放的博客平台，提供文章发布、评论、点赞、收藏等功能。
          任何人注册账号后即可发布内容，无需审核邀请。
        </p>

        <h3>二、账号与安全</h3>
        <ul>
          <li>你需要提供真实、有效的邮箱以便后续验证与找回账号。</li>
          <li>请妥善保管账号密码。因密码泄露导致的损失由账号持有人自行承担。</li>
          <li>不得使用他人身份信息注册，不得转借、出租账号。</li>
          <li>发现账号被盗用的，请立即联系站长处理。</li>
        </ul>

        <h3>三、用户行为规范</h3>
        <p>你在本站发布的所有内容（文章、评论、图片、链接等）应当遵守法律法规，并且不得包含：</p>
        <ul>
          <li>违反国家法律法规、危害国家安全、破坏社会稳定的内容；</li>
          <li>色情、暴力、赌博、诈骗、传销等违法信息；</li>
          <li>人身攻击、侮辱诽谤、侵犯他人隐私或名誉的内容；</li>
          <li>商业广告、垃圾信息、恶意刷屏；</li>
          <li>侵犯他人知识产权的转载内容（未获授权且未注明出处）。</li>
        </ul>
        <p>
          对于违规内容，管理员有权在不事先通知的情况下删除、下架或屏蔽，必要时封禁账号。
          情节严重的，将向有关部门举报。
        </p>

        <h3>四、内容版权</h3>
        <p>
          你发布的内容，版权仍归你所有。但为了让内容能在本站正常展示与传播，
          你授权本站对其进行存储、展示、缓存与必要的格式转换。
          如需删除，可自行删除文章，或联系管理员处理。
        </p>

        <h3>五、协议变更</h3>
        <p>
          本站有权根据运营需要修改本协议。修改后会在本页面更新并标注时间，
          继续使用即视为接受修改后的内容。
        </p>
      </template>

      <!-- ============ 隐私政策 ============ -->
      <template v-else-if="active === 'privacy'">
        <h3>一、我们收集哪些信息</h3>
        <p>为了让站点正常运行并提供基本的统计分析，本站会收集以下信息：</p>
        <table>
          <thead>
            <tr><th>信息类型</th><th>具体内容</th><th>收集时机</th></tr>
          </thead>
          <tbody>
            <tr>
              <td>账号信息</td>
              <td>用户名、昵称、邮箱、头像（注册时填写）</td>
              <td>注册 / 修改资料时</td>
            </tr>
            <tr>
              <td>密码</td>
              <td>经 BCrypt 加盐哈希后的字符串</td>
              <td>注册 / 改密码时</td>
            </tr>
            <tr>
              <td>访问日志</td>
              <td>IP 地址、访问的页面、来源页面、浏览器与操作系统</td>
              <td>每次打开页面时</td>
            </tr>
            <tr>
              <td>互动内容</td>
              <td>你发布的文章、评论、点赞与收藏记录</td>
              <td>使用相应功能时</td>
            </tr>
          </tbody>
        </table>
        <p class="note">
          说明：我们**不收集**你的真实姓名、手机号、身份证号、精确位置等信息，也不会采集通讯录、相册等设备数据。
        </p>

        <h3>二、信息用途</h3>
        <ul>
          <li><strong>账号信息</strong>：用于登录、识别身份、在评论区显示昵称与头像。</li>
          <li><strong>访问日志</strong>：仅用于统计访问量、分析站点运行状况、防范恶意攻击与刷量，<strong>不用于任何商业目的</strong>。</li>
          <li><strong>互动内容</strong>：用于展示你的文章与评论、记录点赞收藏状态。</li>
        </ul>

        <h3>三、密码如何存储</h3>
        <p>
          密码使用 BCrypt 算法加盐哈希后存储，<strong>不保存明文，且无法反向还原</strong>。
          即使是管理员也看不到你的密码。忘记密码时只能通过重设的方式处理。
        </p>

        <h3>四、信息共享</h3>
        <p>
          本站<strong>不会出售、出租或向第三方提供你的个人信息</strong>。
          仅在以下情况下可能披露：
        </p>
        <ul>
          <li>取得你的明确同意；</li>
          <li>依据法律法规要求，或应司法机关、行政机关的合法要求；</li>
          <li>为保护本站、其他用户或公众的合法权益所必需。</li>
        </ul>

        <h3>五、日志保留期限</h3>
        <p>
          访问日志仅保留最近一段时间用于统计分析，超期会定期清理。
          你可以联系管理员申请删除自己的访问记录。账号注销后，相关个人信息将被删除或匿名化处理。
        </p>

        <h3>六、你的权利</h3>
        <ul>
          <li>随时在「个人信息」页查看和修改你的昵称、邮箱、头像。</li>
          <li>随时修改密码。</li>
          <li>删除自己发布的文章与评论。</li>
          <li>要求删除账号及相关数据（联系管理员）。</li>
        </ul>

        <h3>七、Cookie 与本地存储</h3>
        <p>
          本站使用浏览器本地存储保存你的登录凭证（Token），用于保持登录状态。
          本站不使用第三方广告或追踪 Cookie。
        </p>
      </template>

      <!-- ============ 免责声明 ============ -->
      <template v-else>
        <h3>一、内容责任</h3>
        <p>
          本站是开放的内容发布平台，<strong>用户发布的内容仅代表发布者个人观点，不代表本站立场</strong>。
          本站不对用户发布内容的真实性、准确性、合法性作出任何形式的保证。
        </p>
        <p>
          由用户发布内容引起的任何纠纷、损失或法律责任，由发布者自行承担。
          本站已尽合理努力进行管理，但对第三方内容不承担连带责任。
        </p>

        <h3>二、服务可用性</h3>
        <ul>
          <li>本站为个人技术项目，<strong>不承诺服务不中断或完全无差错</strong>。</li>
          <li>因服务器维护、升级、故障、网络中断、不可抗力等原因造成的服务暂停或数据丢失，本站不承担赔偿责任。</li>
          <li>请勿将本站作为唯一的数据存储渠道，重要内容请自行备份。</li>
        </ul>

        <h3>三、外部链接</h3>
        <p>
          本站可能包含指向第三方网站的链接（如友链）。这些链接仅为方便访问而提供，
          本站无法控制其内容，<strong>不对第三方网站的内容、隐私做法或安全性负责</strong>。
          访问第三方网站的风险由你自行承担。
        </p>

        <h3>四、技术内容免责</h3>
        <p>
          本站的技术文章为个人学习记录与经验总结，可能存在错误或版本差异。
          参考文章内容进行操作所造成的任何损失（包括但不限于数据丢失、系统故障、生产事故），
          本站及作者不承担责任。请在生产环境操作前充分测试。
        </p>

        <h3>五、侵权处理</h3>
        <p>
          本站尊重知识产权。如果你认为本站内容侵犯了你的合法权益，
          请提供权属证明与具体链接联系管理员，我们会在核实后及时删除或处理。
        </p>

        <h3>六、账号封禁</h3>
        <p>
          对于发布违法或违规内容的账号，管理员有权在不事先通知的情况下删除内容、
          限制功能或封禁账号。
        </p>

        <h3>七、适用法律</h3>
        <p>
          本声明的解释与适用均以中华人民共和国法律为准。因本站产生的争议，
          双方应友好协商解决；协商不成的，可向本站服务器所在地有管辖权的法院提起诉讼。
        </p>
      </template>

      <p class="updated">最后更新：{{ updatedAt }}</p>
    </div>
  </div>
</template>

<style scoped>
.policy-wrap {
  max-width: 860px;
  margin: 0 auto;
  padding: var(--space-6) var(--space-5);
}

.page-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-2xl);
  color: var(--text-strong);
}

.subtitle {
  margin: 0 0 var(--space-5);
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: 1.7;
}

/* 药丸本体来自全局 .anime-tab（选中态加 .on），这里只补间距 */
.tabs {
  margin-bottom: var(--space-4);
}

.doc {
  padding: var(--space-8) var(--space-8);
  line-height: 1.9;
  color: var(--text-body);
  font-size: var(--text-base);
}

.doc h3 {
  margin: var(--space-6) 0 10px;
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--brand-700);
}

.doc h3:first-child {
  margin-top: 0;
}

.doc p {
  margin: 0 0 var(--space-3);
}

.doc ul {
  margin: 0 0 var(--space-3);
  padding-left: 22px;
}

.doc li {
  margin: 6px 0;
}

.doc strong {
  color: var(--text-strong);
}

/* 政策里有不少表格，别让它把移动端顶破 */
.doc table {
  width: 100%;
  border-collapse: collapse;
  margin: var(--space-3) 0 var(--space-4);
  font-size: var(--text-sm);
}

.doc th,
.doc td {
  border: 1px solid var(--border-soft);
  padding: var(--space-2) var(--space-3);
  text-align: left;
}

.doc th {
  background: var(--surface-sunk);
  color: var(--text-strong);
  font-weight: 600;
}

/* 重点提示块 */
.note {
  background: var(--surface-pink);
  border-left: 3px solid var(--brand-300);
  padding: 10px 14px;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  font-size: var(--text-sm);
}

.updated {
  margin-top: var(--space-8);
  padding-top: 14px;
  border-top: 1px dashed var(--border-soft);
  color: var(--text-muted);
  font-size: 12px;
  text-align: right;
}

@media (max-width: 640px) {
  .doc {
    padding: var(--space-5) var(--space-4);
  }
  /* 药丸在窄屏会自动换行（.anime-tabs 已设 flex-wrap） */
}
</style>

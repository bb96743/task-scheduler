<template>
  <div class="app">
    <header class="topbar">
      <div class="brand">
        <span class="logo">◈</span>
        <div>
          <h1>分布式任务调度中心</h1>
          <p>统一管理散落的定时任务 · 不重复执行 · 失败可闭环</p>
        </div>
      </div>
      <div class="topbar-right">
        <el-tag :type="onlineNodes > 0 ? 'success' : 'info'" effect="light">
          在线节点 {{ onlineNodes }}/{{ nodes.length }}
        </el-tag>
        <el-button size="small" @click="refreshAll">刷新</el-button>
        <el-button size="small" type="primary" @click="openCreate">注册任务</el-button>
      </div>
    </header>

    <section class="stats">
      <div class="stat"><span class="num">{{ tasks.length }}</span><span class="label">任务总数</span></div>
      <div class="stat"><span class="num">{{ enabledCount }}</span><span class="label">启用中</span></div>
      <div class="stat ok"><span class="num">{{ successCount }}</span><span class="label">执行成功</span></div>
      <div class="stat fail"><span class="num">{{ failedCount }}</span><span class="label">执行失败</span></div>
      <div class="stat warn"><span class="num">{{ alerts.length }}</span><span class="label">告警记录</span></div>
    </section>

    <el-tabs v-model="tab" class="tabs">
      <el-tab-pane label="任务列表" name="tasks">
        <el-table :data="tasks" size="small" border stripe>
          <el-table-column prop="id" label="ID" width="50" />
          <el-table-column prop="taskName" label="任务名" min-width="140" show-overflow-tooltip />
          <el-table-column prop="taskGroup" label="分组" width="80" />
          <el-table-column prop="cronExpression" label="cron" width="110" />
          <el-table-column label="分片" width="60" align="center">
            <template #default="{ row }">{{ row.shardTotal }}</template>
          </el-table-column>
          <el-table-column label="重试" width="60" align="center">
            <template #default="{ row }">{{ row.retryMax }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
                {{ row.status === 'ENABLED' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下次触发" width="150">
            <template #default="{ row }">{{ fmt(row.nextFireTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="210">
            <template #default="{ row }">
              <el-button link type="primary" size="small" :disabled="row.status !== 'ENABLED'"
                         @click="run(row)">立即执行</el-button>
              <el-button link type="warning" size="small" @click="toggle(row)">
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </el-button>
              <el-dropdown trigger="click" @command="(cmd) => onCommand(cmd, row)">
                <el-button link size="small">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="config">分片/告警配置</el-dropdown-item>
                    <el-dropdown-item command="executions">执行记录</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`告警记录 (${alerts.length})`" name="alerts">
        <el-table :data="alerts" size="small" border stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column label="任务" width="160">
            <template #default="{ row }">{{ taskName(row.taskId) }}</template>
          </el-table-column>
          <el-table-column prop="consecutiveFail" label="连续失败" width="90" align="center" />
          <el-table-column prop="notifyChannel" label="通道" width="80" />
          <el-table-column prop="message" label="内容" min-width="260" show-overflow-tooltip />
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ fmt(row.triggeredAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="节点" name="nodes">
        <el-table :data="nodes" size="small" border stripe>
          <el-table-column prop="nodeId" label="节点" width="140" />
          <el-table-column prop="host" label="主机" width="180" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后心跳" width="170">
            <template #default="{ row }">{{ fmt(row.lastHeartbeat) }}</template>
          </el-table-column>
        </el-table>
        <p class="hint">双节点部署时，两个节点都会在此出现；同一任务同一触发时刻只会在一个节点产生执行记录。</p>
      </el-tab-pane>
    </el-tabs>

    <!-- 注册任务 -->
    <el-dialog v-model="createVisible" title="注册任务" width="560px">
      <el-form :model="form" label-width="110px" size="small">
        <el-form-item label="任务名" required>
          <el-input v-model="form.taskName" placeholder="例如 order-sync-task" />
        </el-form-item>
        <el-form-item label="分组">
          <el-input v-model="form.taskGroup" placeholder="DEFAULT" />
        </el-form-item>
        <el-form-item label="cron 表达式" required>
          <el-input v-model="form.cronExpression" placeholder="0/30 * * * * ?" />
        </el-form-item>
        <el-form-item label="处理器" required>
          <el-select v-model="form.handlerName" placeholder="选择处理器" style="width: 100%">
            <el-option label="demoSuccessHandler（成功）" value="demoSuccessHandler" />
            <el-option label="demoFailHandler（失败）" value="demoFailHandler" />
            <el-option label="demoShardHandler（分片）" value="demoShardHandler" />
            <el-option label="demoFlakyHandler（偶发失败）" value="demoFlakyHandler" />
          </el-select>
        </el-form-item>
        <el-form-item label="分片数">
          <el-input-number v-model="form.shardTotal" :min="1" :max="64" />
        </el-form-item>
        <el-form-item label="最大重试">
          <el-input-number v-model="form.retryMax" :min="0" :max="10" />
        </el-form-item>
        <el-form-item label="重试间隔(秒)">
          <el-input-number v-model="form.retryInterval" :min="1" :max="3600" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 配置 -->
    <el-dialog v-model="configVisible" title="任务配置" width="520px">
      <el-form label-width="120px" size="small">
        <el-form-item label="分片数">
          <el-input-number v-model="configForm.shardTotal" :min="1" :max="64" />
        </el-form-item>
        <el-divider>告警配置</el-divider>
        <el-form-item label="连续失败阈值">
          <el-input-number v-model="configForm.failThreshold" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="通知通道">
          <el-select v-model="configForm.notifyChannel" style="width: 100%">
            <el-option label="LOG（日志）" value="LOG" />
            <el-option label="WEBHOOK（预留）" value="WEBHOOK" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用告警">
          <el-switch v-model="configForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="configVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 执行记录 -->
    <el-drawer v-model="execVisible" :title="`执行记录 · ${currentTask?.taskName || ''}`" size="86%">
      <div class="drawer-toolbar">
        <el-select v-model="execFilter" size="small" style="width: 140px" @change="loadExecutions">
          <el-option label="全部状态" value="ALL" />
          <el-option label="运行中" value="RUNNING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
        <el-button size="small" @click="loadExecutions">刷新</el-button>
      </div>
      <el-table :data="executions" size="small" border stripe>
        <el-table-column prop="id" label="ID" width="46" />
        <el-table-column label="触发" width="86">
          <template #default="{ row }">
            {{ row.triggerType === 'RETRY' ? `RETRY#${row.retryCount || 1}` : row.triggerType }}
          </template>
        </el-table-column>
        <el-table-column label="节点" width="78">
          <template #default="{ row }">{{ row.nodeId || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" style="white-space: nowrap">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分片(成/败)" width="92" align="center">
          <template #default="{ row }">{{ row.successShard }}/{{ row.failedShard }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="76">
          <template #default="{ row }">{{ row.durationMs }} ms</template>
        </el-table-column>
        <el-table-column label="触发时刻" width="150">
          <template #default="{ row }">{{ fmt(row.fireTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="205">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="openLogs(row)">日志</el-button>
            <el-button link size="small" @click="openShards(row)">分片</el-button>
            <el-button link size="small" type="warning" :disabled="row.status === 'RUNNING'" @click="retryExec(row)">重试</el-button>
            <el-button link size="small" type="danger" :disabled="row.status !== 'FAILED'" @click="compensateExec(row)">补偿</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 日志 -->
    <el-dialog v-model="logVisible" :title="`执行日志 · #${currentExec?.id || ''}`" width="720px">
      <el-radio-group v-model="logLevel" size="small" @change="loadLogs" style="margin-bottom: 10px">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="INFO">INFO</el-radio-button>
        <el-radio-button label="ERROR">ERROR</el-radio-button>
      </el-radio-group>
      <el-table :data="logs" size="small" border height="360">
        <el-table-column prop="createTime" label="时间" width="170">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="logLevel" label="级别" width="80" />
        <el-table-column prop="shardIndex" label="分片" width="70" align="center" />
        <el-table-column prop="message" label="内容" min-width="320" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <!-- 分片 -->
    <el-dialog v-model="shardVisible" :title="`分片明细 · #${currentExec?.id || ''}`" width="640px">
      <el-table :data="shards" size="small" border>
        <el-table-column prop="shardIndex" label="分片" width="70" align="center" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ row.durationMs }} ms</template>
        </el-table-column>
        <el-table-column prop="resultSummary" label="结果摘要" min-width="300" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from './api'

const tab = ref('tasks')
const tasks = ref([])
const alerts = ref([])
const nodes = ref([])

const createVisible = ref(false)
const configVisible = ref(false)
const execVisible = ref(false)
const logVisible = ref(false)
const shardVisible = ref(false)

const currentTask = ref(null)
const currentExec = ref(null)
const executions = ref([])
const logs = ref([])
const shards = ref([])
const execFilter = ref('ALL')
const logLevel = ref('')

const form = reactive({
  taskName: '', taskGroup: 'DEFAULT', cronExpression: '0/30 * * * * ?',
  handlerName: 'demoSuccessHandler', shardTotal: 1, retryMax: 0, retryInterval: 5, remark: ''
})

const configForm = reactive({ shardTotal: 1, failThreshold: 3, notifyChannel: 'LOG', enabled: 1 })

const enabledCount = computed(() => tasks.value.filter(t => t.status === 'ENABLED').length)
const onlineNodes = computed(() => nodes.value.filter(n => n.status === 'ONLINE').length)

// 统计来自最近执行记录（简化：按任务汇总数不入库，这里展示任务维度的累计）
const successCount = ref(0)
const failedCount = ref(0)

function fmt(v) {
  if (!v) return '-'
  // 后端返回形如 2026-11-17T16:31:30.114668，列表只展示到秒，避免换行
  return String(v).replace('T', ' ').replace(/(\.\d{3})\d+$/, '$1').slice(0, 23)
}

function statusType(s) {
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'RUNNING') return 'warning'
  return 'info'
}

function taskName(id) {
  return tasks.value.find(t => t.id === id)?.taskName || `#${id}`
}

async function refreshAll() {
  try {
    const [t, a, n] = await Promise.all([api.listTasks(), api.alerts({ limit: 50 }), api.nodes()])
    tasks.value = t || []
    alerts.value = a || []
    nodes.value = n || []
    await refreshCounters()
  } catch (e) { /* 已由拦截器提示 */ }
}

async function refreshCounters() {
  let ok = 0, bad = 0
  for (const t of tasks.value.slice(0, 10)) {
    try {
      const list = await api.executions(t.id, {})
      ok += list.filter(e => e.status === 'SUCCESS').length
      bad += list.filter(e => e.status === 'FAILED').length
    } catch (e) { /* ignore */ }
  }
  successCount.value = ok
  failedCount.value = bad
}

function openCreate() {
  Object.assign(form, {
    taskName: '', taskGroup: 'DEFAULT', cronExpression: '0/30 * * * * ?',
    handlerName: 'demoSuccessHandler', shardTotal: 1, retryMax: 0, retryInterval: 5, remark: ''
  })
  createVisible.value = true
}

async function submitCreate() {
  await api.createTask({ ...form })
  ElMessage.success('任务已注册')
  createVisible.value = false
  await refreshAll()
}

async function run(row) {
  await api.runNow(row.id)
  ElMessage.success('已触发执行')
  if (execVisible.value && currentTask.value?.id === row.id) await loadExecutions()
}

async function toggle(row) {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await api.updateStatus(row.id, next)
  ElMessage.success(next === 'ENABLED' ? '任务已启用' : '任务已停用')
  await refreshAll()
}

function onCommand(cmd, row) {
  if (cmd === 'config') openConfig(row)
  else if (cmd === 'executions') openExecutions(row)
}

async function openConfig(row) {
  currentTask.value = row
  configForm.shardTotal = row.shardTotal
  try {
    const cfg = await api.getAlert(row.id)
    configForm.failThreshold = cfg?.failThreshold ?? 3
    configForm.notifyChannel = cfg?.notifyChannel ?? 'LOG'
    configForm.enabled = cfg?.enabled ?? 1
  } catch (e) { /* ignore */ }
  configVisible.value = true
}

async function saveConfig() {
  await api.updateShards(currentTask.value.id, configForm.shardTotal)
  await api.updateAlert(currentTask.value.id, {
    failThreshold: configForm.failThreshold,
    notifyChannel: configForm.notifyChannel,
    enabled: configForm.enabled
  })
  ElMessage.success('配置已保存')
  configVisible.value = false
  await refreshAll()
}

async function openExecutions(row) {
  currentTask.value = row
  execFilter.value = 'ALL'
  execVisible.value = true
  await loadExecutions()
}

async function loadExecutions() {
  if (!currentTask.value) return
  const params = execFilter.value && execFilter.value !== 'ALL' ? { status: execFilter.value } : {}
  executions.value = await api.executions(currentTask.value.id, params)
}

async function openLogs(row) {
  currentExec.value = row
  logLevel.value = ''
  logVisible.value = true
  await loadLogs()
}

async function loadLogs() {
  logs.value = await api.logs(currentExec.value.id, logLevel.value ? { level: logLevel.value } : {})
}

async function openShards(row) {
  currentExec.value = row
  shards.value = await api.shards(row.id)
  shardVisible.value = true
}

async function retryExec(row) {
  await api.retry(row.id)
  ElMessage.success('已重新派发')
  await loadExecutions()
}

async function compensateExec(row) {
  await ElMessageBox.confirm('仅对失败终态执行补偿，且同一执行只能补偿一次，确认继续？', '手工补偿', {
    type: 'warning'
  })
  await api.compensate(row.id)
  ElMessage.success('补偿已派发')
  await loadExecutions()
}

let timer = null
onMounted(async () => {
  await refreshAll()
  timer = setInterval(async () => {
    await refreshAll()
    if (execVisible.value) await loadExecutions()
  }, 5000)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style>
:root { --brand: #1f6feb; }
body { margin: 0; background: #f5f7fa; font-family: -apple-system, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; }
.app { max-width: 1180px; margin: 0 auto; padding: 20px 24px 40px; }
.topbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.brand { display: flex; align-items: center; gap: 12px; }
.logo { font-size: 26px; color: var(--brand); }
.brand h1 { font-size: 18px; margin: 0; font-weight: 600; color: #1f2328; }
.brand p { margin: 2px 0 0; font-size: 12px; color: #6b7280; }
.topbar-right { display: flex; align-items: center; gap: 10px; }
.stats { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 8px; }
.stat { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 14px 16px; display: flex; flex-direction: column; gap: 4px; }
.stat .num { font-size: 22px; font-weight: 600; color: #1f2328; }
.stat .label { font-size: 12px; color: #6b7280; }
.stat.ok .num { color: #16a34a; }
.stat.fail .num { color: #dc2626; }
.stat.warn .num { color: #d97706; }
.tabs { background: #fff; border-radius: 10px; padding: 4px 16px 16px; border: 1px solid #e5e7eb; }
.drawer-toolbar { display: flex; gap: 10px; margin-bottom: 12px; }
.hint { font-size: 12px; color: #6b7280; margin-top: 10px; }
</style>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined, UserOutlined } from '@ant-design/icons-vue'
import { changePassword, fetchMe, updateProfile } from '../../api/auth'
import { getToken, getUser, setSession } from '../../auth/session'

const loading = ref(false)
const savingProfile = ref(false)
const savingPassword = ref(false)

const profile = reactive({
  username: '',
  displayName: '',
  avatar: '',
})

const pwd = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const avatarSrc = computed(() => (profile.avatar ? profile.avatar : undefined))

onMounted(() => {
  load()
})

async function load() {
  loading.value = true
  try {
    const me = await fetchMe()
    applyUser(me)
  } catch (e) {
    const local = getUser()
    if (local) applyUser(local)
    message.error(e.message || '加载资料失败')
  } finally {
    loading.value = false
  }
}

function applyUser(u) {
  profile.username = u?.username || ''
  profile.displayName = u?.displayName || u?.username || ''
  profile.avatar = u?.avatar || ''
  setSession(getToken(), {
    ...(getUser() || {}),
    ...u,
  })
}

async function saveProfile(extra = {}) {
  const name = (extra.displayName ?? profile.displayName ?? '').trim()
  if (!name) {
    message.warning('请填写用户名称')
    return false
  }
  const avatar = extra.avatar !== undefined ? extra.avatar : profile.avatar || ''
  savingProfile.value = true
  try {
    const me = await updateProfile({
      displayName: name,
      avatar,
    })
    applyUser(me)
    return true
  } catch (e) {
    message.error(e.message || '保存失败')
    return false
  } finally {
    savingProfile.value = false
  }
}

async function onSaveProfile() {
  const ok = await saveProfile()
  if (ok) message.success('资料已保存')
}

async function onClearAvatar() {
  profile.avatar = ''
  const ok = await saveProfile({ avatar: '' })
  if (ok) message.success('头像已清除')
}

function beforeAvatarUpload(file) {
  if (!file?.type?.startsWith('image/')) {
    message.warning('请选择图片文件')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    message.warning('图片请小于 2MB')
    return false
  }
  const reader = new FileReader()
  reader.onload = async () => {
    try {
      const out = await compressAvatar(String(reader.result || ''))
      profile.avatar = out
      const ok = await saveProfile({ avatar: out })
      if (ok) message.success('头像已更新')
    } catch {
      message.error('头像处理失败')
    }
  }
  reader.onerror = () => message.error('读取图片失败')
  reader.readAsDataURL(file)
  return false
}

function compressAvatar(dataUrl) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      const size = 160
      const canvas = document.createElement('canvas')
      canvas.width = size
      canvas.height = size
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        reject(new Error('canvas'))
        return
      }
      // 居中裁切为正方形，避免拉伸变形
      const min = Math.min(img.width, img.height) || 1
      const sx = (img.width - min) / 2
      const sy = (img.height - min) / 2
      ctx.fillStyle = '#f5f5f5'
      ctx.fillRect(0, 0, size, size)
      ctx.drawImage(img, sx, sy, min, min, 0, 0, size, size)
      resolve(canvas.toDataURL('image/jpeg', 0.85))
    }
    img.onerror = () => reject(new Error('image'))
    img.src = dataUrl
  })
}

async function onChangePassword() {
  if (!pwd.currentPassword || !pwd.newPassword) {
    message.warning('请填写当前密码与新密码')
    return
  }
  if (pwd.newPassword.length < 6) {
    message.warning('新密码至少 6 位')
    return
  }
  if (pwd.newPassword !== pwd.confirmPassword) {
    message.warning('两次输入的新密码不一致')
    return
  }
  savingPassword.value = true
  try {
    await changePassword({
      currentPassword: pwd.currentPassword,
      newPassword: pwd.newPassword,
    })
    pwd.currentPassword = ''
    pwd.newPassword = ''
    pwd.confirmPassword = ''
    message.success('密码已修改')
  } catch (e) {
    message.error(e.message || '修改密码失败')
  } finally {
    savingPassword.value = false
  }
}
</script>

<template>
  <div class="profile-page">
    <a-card title="个人资料" :loading="loading">
      <a-form layout="vertical" class="profile-form">
        <a-form-item label="头像">
          <div class="avatar-row">
            <a-avatar class="profile-avatar" :size="72" :src="avatarSrc">
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <div class="avatar-actions">
              <a-upload :show-upload-list="false" accept="image/*" :before-upload="beforeAvatarUpload">
                <a-button :loading="savingProfile">
                  <UploadOutlined />
                  选择图片
                </a-button>
              </a-upload>
              <a-button v-if="profile.avatar" type="link" danger :disabled="savingProfile" @click="onClearAvatar">
                清除
              </a-button>
              <div class="hint">选择后自动保存；非正方形会居中裁切，不会拉伸</div>
            </div>
          </div>
        </a-form-item>

        <a-form-item label="登录账号">
          <a-input :value="profile.username" disabled />
        </a-form-item>

        <a-form-item label="用户名称" required>
          <a-input
            v-model:value="profile.displayName"
            maxlength="64"
            placeholder="显示名称"
            @pressEnter="onSaveProfile"
          />
        </a-form-item>

        <a-form-item>
          <a-button type="primary" :loading="savingProfile" @click="onSaveProfile">保存资料</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card title="修改密码" class="pwd-card">
      <a-form layout="vertical" class="profile-form">
        <a-form-item label="当前密码" required>
          <a-input-password v-model:value="pwd.currentPassword" autocomplete="current-password" />
        </a-form-item>
        <a-form-item label="新密码" required>
          <a-input-password
            v-model:value="pwd.newPassword"
            autocomplete="new-password"
            placeholder="至少 6 位"
          />
        </a-form-item>
        <a-form-item label="确认新密码" required>
          <a-input-password
            v-model:value="pwd.confirmPassword"
            autocomplete="new-password"
            @pressEnter="onChangePassword"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="savingPassword" @click="onChangePassword">修改密码</a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.profile-page {
  max-width: 640px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.profile-form {
  max-width: 420px;
}
.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.profile-avatar {
  flex: 0 0 72px !important;
  width: 72px !important;
  height: 72px !important;
  min-width: 72px !important;
  max-width: 72px !important;
}
.profile-avatar :deep(img) {
  width: 100% !important;
  height: 100% !important;
  object-fit: cover;
  display: block;
}
.avatar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}
.hint {
  width: 100%;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}
.pwd-card {
  margin-top: 0;
}
</style>

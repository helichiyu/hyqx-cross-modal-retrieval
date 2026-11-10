<script setup lang="ts">
import { ref } from 'vue'

const query = ref('')
</script>

<template>
  <div>
    <h2 class="cms-page-title">
      跨模态检索
    </h2>
    <el-card shadow="never">
      <el-input
        v-model="query"
        placeholder="输入文本描述，检索图像 / 音频 / 视频"
        clearable
        size="large"
        disabled
      >
        <template #append>
          <el-button disabled>
            检索
          </el-button>
        </template>
      </el-input>
      <p class="cms-placeholder cms-tip">
        检索算子尚未迁入本工程（9 个算子、单容器 6300 端口）。本页保留界面位置，
        算子接口迁移完成并完成鉴权后在此接入，不在前端伪造检索结果。
      </p>
    </el-card>
  </div>
</template>

<style scoped>
.cms-tip {
  margin-top: 16px;
}
</style>

import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import VueKonva from 'vue-konva'
import App from './App.vue'
import router from './router'
import 'ant-design-vue/dist/reset.css'
import './styles/main.css'

createApp(App).use(router).use(Antd).use(VueKonva).mount('#app')

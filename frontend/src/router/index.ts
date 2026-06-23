import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getAccessToken } from '../utils/authToken'
import LoginView from '../views/auth/LoginView.vue'
import SignupView from '../views/auth/SignupView.vue'
import IntegrationChannelsView from '../views/management/IntegrationChannelsView.vue'
import NotificationSettingsView from '../views/management/NotificationSettingsView.vue'
import UserManagementView from '../views/management/UserManagementView.vue'
import UserSettingsView from '../views/management/UserSettingsView.vue'
import ProjectCreateView from '../views/project/ProjectCreateView.vue'
import ProjectDetailDeploymentsTab from '../views/project-detail/tabs/ProjectDetailDeploymentsTab.vue'
import ProjectsView from '../views/project/ProjectsView.vue'
import ProjectDetailView from '../views/project-detail/ProjectDetailView.vue'
import ProjectDetailOverviewTab from '../views/project-detail/tabs/ProjectDetailOverviewTab.vue'
import ProjectDetailPlaceholderTab from '../views/project-detail/tabs/ProjectDetailPlaceholderTab.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/login',
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: {
      layout: 'auth',
    },
  },
  {
    path: '/signup',
    name: 'signup',
    component: SignupView,
    meta: {
      layout: 'auth',
    },
  },
  {
    path: '/projects',
    name: 'projects',
    component: ProjectsView,
    meta: {
      requiresAuth: true,
    },
  },
  {
    path: '/projects/new',
    name: 'project-create',
    component: ProjectCreateView,
    meta: {
      requiresAuth: true,
    },
  },
  {
    path: '/projects/:projectId',
    component: ProjectDetailView,
    props: (route) => ({
      projectId: Number(route.params.projectId),
    }),
    redirect: (to) => ({
      name: 'project-detail-overview',
      params: {
        projectId: to.params.projectId,
      },
    }),
    children: [
      {
        path: 'overview',
        name: 'project-detail-overview',
        component: ProjectDetailOverviewTab,
      },
      {
        path: 'deployments',
        name: 'project-detail-deployments',
        component: ProjectDetailDeploymentsTab,
      },
      {
        path: 'environment',
        name: 'project-detail-environment',
        component: ProjectDetailPlaceholderTab,
        props: {
          title: '환경 변수',
          description: '환경 변수 탭 화면은 프로젝트별 변수 목록, 마스킹, 수정 기능을 연결할 예정입니다.',
        },
      },
      {
        path: 'webhook',
        name: 'project-detail-webhook',
        component: ProjectDetailPlaceholderTab,
        props: {
          title: 'Webhook',
          description: 'Webhook 탭 화면은 GitHub Webhook URL, Secret, 최근 호출 상태를 보여주도록 확장할 예정입니다.',
        },
      },
      {
        path: 'monitoring',
        name: 'project-detail-monitoring',
        component: ProjectDetailPlaceholderTab,
        props: {
          title: '모니터링',
          description: '모니터링 탭 화면은 CPU, 메모리, 트래픽 지표와 런타임 상태를 연동할 예정입니다.',
        },
      },
      {
        path: 'logs',
        name: 'project-detail-logs',
        component: ProjectDetailPlaceholderTab,
        props: {
          title: '로그',
          description: '로그 탭 화면은 배포 및 런타임 로그 스트림을 조회할 수 있게 연결할 예정입니다.',
        },
      },
    ],
    meta: {
      requiresAuth: true,
    },
  },
  {
    path: '/integration-channels',
    name: 'integration-channels',
    component: IntegrationChannelsView,
    meta: {
      requiresAuth: true,
    },
  },
  {
    path: '/notification-settings',
    name: 'notification-settings',
    component: NotificationSettingsView,
    meta: {
      requiresAuth: true,
    },
  },
  {
    path: '/user-management',
    name: 'user-management',
    component: UserManagementView,
    meta: {
      requiresAuth: true,
    },
  },
  {
    path: '/user-settings',
    name: 'user-settings',
    component: UserSettingsView,
    meta: {
      requiresAuth: true,
    },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach((to) => {
  const isLoggedIn = Boolean(getAccessToken())

  if (to.meta.requiresAuth && !isLoggedIn) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if ((to.name === 'login' || to.name === 'signup') && isLoggedIn) {
    return {
      name: 'projects',
    }
  }

  return true
})

export default router

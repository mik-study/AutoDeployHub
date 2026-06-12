import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getAccessToken } from '../utils/authToken'
import IntegrationChannelsView from '../views/IntegrationChannelsView.vue'
import LoginView from '../views/LoginView.vue'
import NotificationSettingsView from '../views/NotificationSettingsView.vue'
import ProjectCreateView from '../views/ProjectCreateView.vue'
import ProjectsView from '../views/ProjectsView.vue'
import SignupView from '../views/SignupView.vue'
import UserSettingsView from '../views/UserSettingsView.vue'
import UserManagementView from '../views/UserManagementView.vue'

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

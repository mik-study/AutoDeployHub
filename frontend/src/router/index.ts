import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getAccessToken } from '../utils/authToken'
import LoginView from '../views/LoginView.vue'
import ProjectsView from '../views/ProjectsView.vue'

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
    path: '/projects',
    name: 'projects',
    component: ProjectsView,
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

  if (to.name === 'login' && isLoggedIn) {
    return {
      name: 'projects',
    }
  }

  return true
})

export default router

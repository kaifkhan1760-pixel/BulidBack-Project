import { Navigate, Route, Routes } from 'react-router'
import { HomeRedirect, ProtectedRoute, PublicOnlyRoute } from './components/RouteGuards'
import AdminPage from './pages/AdminPage'
import OrderDetailPage from './pages/OrderDetailPage'
import OrdersPage from './pages/OrdersPage'
import CataloguePage from './pages/CataloguePage'
import ConnectionCheckPage from './pages/ConnectionCheckPage'
import DashboardPage from './pages/DashboardPage'
import LoginPage from './pages/LoginPage'
import MaterialDetailPage from './pages/MaterialDetailPage'
import MaterialFormPage from './pages/MaterialFormPage'
import RegisterPage from './pages/RegisterPage'
import SellerMaterialsPage from './pages/SellerMaterialsPage'
import UnauthorizedPage from './pages/UnauthorizedPage'
import './styles/app.css'
import './styles/materials.css'
import './styles/orders.css'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />

      <Route path="/login" element={<PublicOnlyRoute><LoginPage /></PublicOnlyRoute>} />
      <Route path="/register" element={<PublicOnlyRoute><RegisterPage /></PublicOnlyRoute>} />

      <Route
        path="/buyer/dashboard"
        element={<ProtectedRoute role="BUYER"><DashboardPage role="BUYER" /></ProtectedRoute>}
      />
      <Route
        path="/seller/dashboard"
        element={<ProtectedRoute role="SELLER"><DashboardPage role="SELLER" /></ProtectedRoute>}
      />
      <Route
        path="/admin/dashboard"
        element={<ProtectedRoute role="ADMIN"><DashboardPage role="ADMIN" /></ProtectedRoute>}
      />

      {/* Public catalogue: anyone can browse */}
      <Route path="/materials" element={<CataloguePage />} />
      <Route path="/materials/:id" element={<MaterialDetailPage />} />

      {/* Seller inventory */}
      <Route
        path="/seller/materials"
        element={<ProtectedRoute role="SELLER"><SellerMaterialsPage /></ProtectedRoute>}
      />
      <Route
        path="/seller/materials/new"
        element={<ProtectedRoute role="SELLER"><MaterialFormPage mode="create" /></ProtectedRoute>}
      />
      <Route
        path="/seller/materials/:id/edit"
        element={<ProtectedRoute role="SELLER"><MaterialFormPage key="edit" mode="edit" /></ProtectedRoute>}
      />

      {/* Orders and payments */}
      <Route path="/buyer/orders" element={<ProtectedRoute role="BUYER"><OrdersPage view="buyer" /></ProtectedRoute>} />
      <Route path="/buyer/orders/:id" element={<ProtectedRoute role="BUYER"><OrderDetailPage /></ProtectedRoute>} />
      <Route path="/seller/orders" element={<ProtectedRoute role="SELLER"><OrdersPage view="seller" /></ProtectedRoute>} />

      {/* Admin console */}
      <Route path="/admin/console" element={<ProtectedRoute role="ADMIN"><AdminPage /></ProtectedRoute>} />

      <Route path="/unauthorized" element={<UnauthorizedPage />} />
      <Route path="/connection-check" element={<ConnectionCheckPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

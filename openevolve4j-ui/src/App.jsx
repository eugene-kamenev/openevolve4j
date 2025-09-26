import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from './components/AppLayout.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Models from './pages/Models.jsx';
import Problems from './pages/Problems.jsx';
import ProblemDetail from './pages/ProblemDetail.jsx';

function App() {
	return (
		<BrowserRouter>
			<Routes>
				<Route element={<AppLayout />}>
					<Route index element={<Dashboard />} />
					<Route path="models" element={<Models />} />
					<Route path="problems" element={<Problems />} />
					<Route path="problems/:id" element={<ProblemDetail />} />
				</Route>
				<Route path="*" element={<Navigate to="/" replace />} />
			</Routes>
		</BrowserRouter>
	);
}

export default App;

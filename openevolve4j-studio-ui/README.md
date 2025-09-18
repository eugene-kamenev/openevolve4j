# OpenEvolve4j Studio UI

A React-based web interface for managing and monitoring OpenEvolve4j evolution experiments.

## Features

- **Dashboard**: Overview of your evolution experiments with key metrics and recent activity
- **Problems Management**: Create, edit, and manage evolution problem configurations
- **Runs Management**: Monitor and control evolution runs, view progress and status
- **Solutions Browser**: Explore generated solutions, view fitness metrics and lineage
- **Real-time Updates**: Live monitoring of evolution state and progress

## Getting Started

### Prerequisites

- Node.js 18+ 
- npm or yarn
- OpenEvolve4j Studio backend running on port 8080

### Installation

1. Install dependencies:
```bash
npm install
```

2. Configure API endpoint (optional):
Edit `.env` file to change the backend URL:
```
VITE_API_BASE_URL=http://localhost:8080
```

3. Start the development server:
```bash
npm run dev
```

4. Open your browser to `http://localhost:5173`

### Building for Production

```bash
npm run build
```

## API Integration

The UI connects to the OpenEvolve4j Studio backend REST API endpoints:

- `/evolution` - Evolution Problems management
- `/run` - Evolution Runs management  
- `/solution` - Evolution Solutions browsing
- `/state` - Evolution State monitoring

## Architecture

### Components Structure

- **`components/common/`** - Reusable UI components (buttons, modals, forms)
- **`components/dashboard/`** - Dashboard views and widgets
- **`components/problems/`** - Problem management interfaces
- **`components/runs/`** - Run monitoring and control
- **`components/solutions/`** - Solution browsing and analysis

### Services

- **`services/api.js`** - Base API service with CRUD operations
- **`services/evolutionProblemService.js`** - Problem-specific API calls
- **`services/evolutionRunService.js`** - Run management API calls
- **`services/evolutionSolutionService.js`** - Solution querying API calls
- **`services/evolutionStateService.js`** - State monitoring API calls

### Hooks

- **`useApiData`** - Fetch and manage API data with loading/error states
- **`usePaginatedData`** - Handle paginated API responses
- **`useForm`** - Form state management with validation
- **`useModal`** - Modal dialog state management

## Key Features

### Evolution Problems
- Create complex evolution configurations with visual forms
- Configure MAP-Elites parameters, LLM settings, fitness metrics
- Import/export problem configurations
- View problem details and related runs

### Evolution Runs  
- Start/stop evolution processes
- Monitor run progress and current state
- View run history and statistics
- Real-time status updates

### Solutions Management
- Browse generated solutions with sorting and filtering
- View solution code, fitness scores, and metadata
- Explore solution lineage and ancestry
- Compare solution performance

### Dashboard Analytics
- System overview with key metrics
- Recent activity monitoring
- Top-performing solutions
- Active runs status

## Development

### Code Style
- ESLint configuration for code quality
- Functional React components with hooks
- Tailwind CSS for styling
- Modular service architecture

### API Error Handling
- Comprehensive error states in UI
- Retry mechanisms for failed requests
- User-friendly error messages
- Network failure handling

### State Management
- React hooks for local component state
- Custom hooks for API data management
- No external state management library needed

## Configuration

### Environment Variables
- `VITE_API_BASE_URL` - Backend API base URL (default: http://localhost:8080)
- `VITE_NODE_ENV` - Environment mode

### Styling
- Tailwind CSS utility classes
- Responsive design for mobile/desktop
- Dark/light mode support
- Custom component library

## Troubleshooting

### Common Issues

1. **API Connection Failed**
   - Ensure OpenEvolve4j Studio backend is running
   - Check API URL in `.env` file
   - Verify CORS settings on backend

2. **Build Errors**
   - Clear node_modules and reinstall: `rm -rf node_modules && npm install`
   - Check Node.js version compatibility

3. **Styling Issues** 
   - Ensure Tailwind CSS is properly configured
   - Check PostCSS configuration

## Contributing

1. Follow existing code style and patterns
2. Add proper error handling for new API calls
3. Include loading states for async operations
4. Write reusable components when possible
5. Test UI interactions thoroughly

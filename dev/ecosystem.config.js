// `clj -M:dev` runs infra.dev.server, which opens the GLFW window and runs
// Phase 0: a stellar nebula collapsing into a solar system (full simulation
// pipeline, auto-reseeding to a fresh nebula when a system finishes forming).
// After editing Clojure sources: `pm2 restart gates-of-truth-dev` to recompile.
//
// `clj -M:notebook` starts a Jupyter Lab server with the clojupyter kernel.
// It shares the same nREPL port (7888) as the simulation so notebooks can
// evaluate forms directly in the running world state.
// Access at http://localhost:8888
module.exports = {
  apps: [
    {
      name: 'gates-of-truth-dev',
      script: 'clj',
      args: '-M:dev',
      cwd: '/home/err/spaces/Truth',
      env: {
        DISPLAY: ':0'
      },
      interpreter: 'none',
      autorestart: true,
      max_restarts: 5,
      min_uptime: '10s',
      kill_timeout: 5000,
      wait_ready: false,
      listen_timeout: 30000,
      log_date_format: 'YYYY-MM-DD HH:mm:ss Z'
    },
    {
      name: 'truth-notebook',
      script: 'jupyter',
      args: 'lab --port 8888 --no-browser --ip 0.0.0.0',
      cwd: '/home/err/spaces/Truth',
      env: {
        DISPLAY: ':0'
      },
      interpreter: 'none',
      autorestart: true,
      max_restarts: 3,
      min_uptime: '5s',
      kill_timeout: 5000,
      wait_ready: false,
      listen_timeout: 30000,
      log_date_format: 'YYYY-MM-DD HH:mm:ss Z'
    }
  ]
};

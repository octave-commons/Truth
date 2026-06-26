// `clj -M:dev` runs infra.dev.server, which opens the GLFW window and runs
// Phase 0: a stellar nebula collapsing into a solar system (full simulation
// pipeline, auto-reseeding to a fresh nebula when a system finishes forming).
// After editing Clojure sources: `pm2 restart gates-of-truth-dev` to recompile.
module.exports = {
  apps: [{
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
  }]
};

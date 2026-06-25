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

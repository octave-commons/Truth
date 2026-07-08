# Hourly schedule

This actor is invoked automatically every hour by a systemd user timer.

- **Timer unit**: `fork-tax-tender.timer`
- **Service unit**: `fork-tax-tender.service`
- **Interval**: `1h` (`OnUnitActiveSec=1h`)
- **First run**: 5 minutes after user boot (`OnBootSec=5min`)
- **Persistent**: yes, missed triggers run on next boot

The timer dispatches the actor via `runtime/runner.sh` in non-interactive mode. The actor is one-shot: it checks, acts if needed, records a receipt, and exits.

To inspect the timer:

```bash
systemctl --user status fork-tax-tender.timer
systemctl --user list-timers fork-tax-tender.timer
```

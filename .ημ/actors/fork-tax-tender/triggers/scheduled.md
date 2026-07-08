# Scheduled trigger

This actor fires on a schedule, not on a message. The systemd timer passes a standard command message:

> "Check for significant changes in the Gates of Truth repository. If significant, pay the fork tax. Otherwise record a no-op receipt and exit."

The actor should not wait for additional inbox messages. Process the command, write the result to `outbox/` and `receipts.edn`, and exit.
